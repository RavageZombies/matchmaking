/*-
 * #%L
 * matchmaking.server
 * %%
 * Copyright (C) 2016 - 2019 Frederik Kammel
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
package com.github.vatbub.matchmaking.server.logic.roomproviders

import com.github.vatbub.matchmaking.testutils.TestUtils
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MemoryRoomProviderTest : RoomProviderTest<MemoryRoomProvider>() {
    override fun getCloneOf(instance: MemoryRoomProvider): MemoryRoomProvider {
        val result = MemoryRoomProvider()
        instance.rooms.forEach { (key, value) -> result.rooms[key] = value }
        return result
    }

    override fun newObjectUnderTest() = MemoryRoomProvider()

    @Test
    override fun notEqualsTest() {
        val object1 = newObjectUnderTest()
        val object2 = newObjectUnderTest()
        object2.createNewRoom(TestUtils.getRandomHexString())
        Assertions.assertNotEquals(object1, object2)
    }
}
