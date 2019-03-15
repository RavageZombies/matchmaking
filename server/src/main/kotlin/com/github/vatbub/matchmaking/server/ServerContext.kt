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
package com.github.vatbub.matchmaking.server

import com.github.vatbub.matchmaking.server.handlers.*
import com.github.vatbub.matchmaking.server.idprovider.ConnectionIdProvider
import com.github.vatbub.matchmaking.server.idprovider.MemoryIdProvider
import com.github.vatbub.matchmaking.server.roomproviders.MemoryRoomProvider
import com.github.vatbub.matchmaking.server.roomproviders.RoomProvider

/**
 * Contains configuration information for the server
 */
data class ServerContext(
    var connectionIdProvider: ConnectionIdProvider = MemoryIdProvider(),
    var messageDispatcher: MessageDispatcher = MessageDispatcher(connectionIdProvider),
    var roomProvider: RoomProvider = MemoryRoomProvider()
) {

    /**
     * Removes all handlers from the [messageDispatcher] and reinstantiates the default handlers
     */
    fun resetMessageHandlers() {
        messageDispatcher.removeAllHandlers()
        messageDispatcher.registerHandler(GetConnectionIdHandler(connectionIdProvider))
        messageDispatcher.registerHandler(JoinOrCreateRoomRequestHandler(roomProvider))
        messageDispatcher.registerHandler(DestroyRoomRequestHandler(roomProvider))
        messageDispatcher.registerHandler(DisconnectRequestHandler(roomProvider))
        messageDispatcher.registerHandler(GetRoomDataRequestHandler(roomProvider))
        messageDispatcher.registerHandler(SendDataToHostRequestHandler(roomProvider))
        messageDispatcher.registerHandler(StartGameRequestHandler(roomProvider))
        messageDispatcher.registerHandler(SubscribeToRoomRequestHandler(roomProvider))
        messageDispatcher.registerHandler(UpdateGameStateRequestHandler(roomProvider))
    }
}
