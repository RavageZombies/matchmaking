/*-
 * #%L
 * matchmaking.common
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
package com.github.vatbub.matchmaking.common

/**
 * Superclass for all requests that can be sent over the network
 */
open class Request(override val connectionId: String?, val password: String?, override val className: String, var requestId: String?) :
        ServerInteraction {
    /**
     * Do not remove! Used by KryoNet.
     */
    internal constructor() : this(null, null, "", null)

    @Suppress("UNCHECKED_CAST")
    override fun copy() = Request(connectionId, password, className, requestId)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Request) return false

        if (connectionId != other.connectionId) return false
        if (className != other.className) return false
        if (requestId != other.requestId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = connectionId?.hashCode() ?: 0
        result = 31 * result + className.hashCode()
        result = 31 * result + (requestId?.hashCode() ?: 0)
        return result
    }

    override val protocolVersion = ServerInteraction.defaultProtocolVersion
}
