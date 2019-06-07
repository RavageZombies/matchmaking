/*-
 * #%L
 * matchmaking.server
 * %%
 * Copyright (C) 2016 - 2018 Frederik Kammel
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package com.github.vatbub.matchmaking.server.logic

import com.github.vatbub.matchmaking.common.Request
import com.github.vatbub.matchmaking.common.Response
import com.github.vatbub.matchmaking.common.logger
import com.github.vatbub.matchmaking.common.responses.AuthorizationException
import com.github.vatbub.matchmaking.common.responses.BadRequestException
import com.github.vatbub.matchmaking.common.responses.InternalServerErrorException
import com.github.vatbub.matchmaking.common.responses.UnknownConnectionIdException
import com.github.vatbub.matchmaking.server.logic.handlers.RequestHandler
import com.github.vatbub.matchmaking.server.logic.handlers.RequestHandlerWithWebsocketSupport
import com.github.vatbub.matchmaking.server.logic.idprovider.AuthorizationResult.*
import com.github.vatbub.matchmaking.server.logic.idprovider.ConnectionIdProvider
import com.github.vatbub.matchmaking.server.logic.idprovider.Id
import com.github.vatbub.matchmaking.server.logic.sockets.Session
import java.net.Inet4Address
import java.net.Inet6Address

/**
 * Dispatches received requests among the registered [RequestHandler]s.
 */
class MessageDispatcher(var connectionIdProvider: ConnectionIdProvider) {
    internal val handlers: MutableList<RequestHandler<*>> = mutableListOf()

    /**
     * Iterates through all registered [RequestHandler]s and dispatches the request to the first handler which
     * specifies that it can handle the request.
     * @return The response returned by the handler or `null` if no suitable handler was found.
     */
    fun dispatch(
            request: Request,
            sourceIp: Inet4Address?,
            sourceIpv6: Inet6Address?,
            websocketSession: Session? = null
    ): Response? {
        logger.trace("Dispatching request of type ${request.className}...")
        for (handler in handlers) {
            if (!handler.canHandle(request)) continue
            if (handler is RequestHandlerWithWebsocketSupport && handler.requiresSocket && websocketSession == null)
                continue
            @Suppress("UNCHECKED_CAST")
            handler as RequestHandler<Request>
            logger.trace("Found a matching handler")
            if (!handler.needsAuthentication(request))
                return invokeHandler(handler, request, sourceIp, sourceIpv6, websocketSession)

            return when (connectionIdProvider.isAuthorized(Id(request.connectionId, request.password))) {
                NotFound -> UnknownConnectionIdException("The specified connection id is not known to the server")
                NotAuthorized -> AuthorizationException("Incorrect password")
                Authorized -> invokeHandler(handler, request, sourceIp, sourceIpv6, websocketSession)
            }
        }

        logger.warn("No matching handler found for a request of type ${request.className}")
        return null
    }

    private fun <T : Request> invokeHandler(
            handler: RequestHandler<T>,
            request: T,
            sourceIp: Inet4Address?,
            sourceIpv6: Inet6Address?,
            websocketSession: Session?
    ): Response {
        logger.trace("Invoking the corresponding handler...")
        if (handler is RequestHandlerWithWebsocketSupport && websocketSession != null) {
            logger.trace("Invoking a socket handler with a session object...")
            return handler.handle(websocketSession, request, sourceIp, sourceIpv6)
        }
        return handler.handle(request, sourceIp, sourceIpv6)
    }

    fun dispatchOrCreateException(
            request: Request,
            sourceIp: Inet4Address?,
            sourceIpv6: Inet6Address?,
            websocketSession: Session? = null
    ): Response {
        var responseInteraction: Response? = try {
            dispatch(request, sourceIp, sourceIpv6, websocketSession)
        } catch (e: IllegalArgumentException) {
            BadRequestException(e.javaClass.name + ", " + e.message)
        } catch (e: Exception) {
            logger.error("An internal server error occurred:", e)
            InternalServerErrorException(e.javaClass.name + ", " + e.message)
        }

        if (responseInteraction == null) {
            val e = IllegalStateException("No response generated by server")
            logger.error(e.message, e)
            responseInteraction = InternalServerErrorException(e.javaClass.name + ", " + e.message)
        }

        responseInteraction.responseTo = request.requestId

        return responseInteraction
    }

    fun dispatchWebsocketSessionClosed(session: Session) {
        handlers.forEach {
            if (it is RequestHandlerWithWebsocketSupport)
                it.onSessionClosed(session)
        }
    }

    fun registerHandler(handler: RequestHandler<*>) {
        if (!isHandlerRegistered(handler))
            handlers.add(handler)
    }

    fun isHandlerRegistered(handler: RequestHandler<*>): Boolean {
        return handlers.contains(handler)
    }

    fun removeHandler(handler: RequestHandler<*>): Boolean {
        return handlers.remove(handler)
    }

    fun removeAllHandlers() {
        handlers.clear()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MessageDispatcher

        if (connectionIdProvider != other.connectionIdProvider) return false
        if (handlers != other.handlers) return false

        return true
    }

    override fun hashCode(): Int {
        var result = connectionIdProvider.hashCode()
        result = 31 * result + handlers.hashCode()
        return result
    }
}
