/*-
 * #%L
 * matchmaking.test-utils
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
package com.github.vatbub.matchmaking.testutils

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

abstract class KotlinTestSuperclassWithExceptionHandlerForMultithreading<T> : KotlinTestSuperclass<T>() {
    private var previousExceptionHandler: Thread.UncaughtExceptionHandler? = null
    private var mainThread: Thread? = null
    private val uncaughtExceptions = mutableListOf<Throwable>()
    private val testExceptionHandler = object : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(t: Thread?, e: Throwable?) {
            if (e == null) return
            if (t == mainThread)
                throw e
            System.err.println("Uncaught exception in thread ${t?.name
                    ?: "null"}, will be rethrown at the end of the test...")
            e.printStackTrace()

            uncaughtExceptions.add(e)
        }
    }

    @BeforeEach
    fun setExceptionHandler() {
        uncaughtExceptions.clear()
        mainThread = Thread.currentThread()
        previousExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(testExceptionHandler)
    }

    @AfterEach
    fun throwExceptionsFromOtherThreadsAndResetExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler(previousExceptionHandler)
        if (uncaughtExceptions.isNotEmpty()) System.err.println("Rethrowing uncaught exceptions...")
        uncaughtExceptions.forEach { throw it }
        uncaughtExceptions.clear()
    }
}
