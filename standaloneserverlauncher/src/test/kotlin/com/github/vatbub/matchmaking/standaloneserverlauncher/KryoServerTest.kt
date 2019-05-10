package com.github.vatbub.matchmaking.standaloneserverlauncher

import com.esotericsoftware.kryonet.Connection
import com.esotericsoftware.kryonet.FrameworkMessage
import com.esotericsoftware.kryonet.Listener
import com.github.vatbub.matchmaking.common.KryoCommon
import com.github.vatbub.matchmaking.common.Request
import com.github.vatbub.matchmaking.common.Response
import com.github.vatbub.matchmaking.common.responses.BadRequestException
import com.github.vatbub.matchmaking.common.responses.InternalServerErrorException
import com.github.vatbub.matchmaking.common.responses.ServerInteractionException
import com.github.vatbub.matchmaking.common.testing.dummies.DummyRequest
import com.github.vatbub.matchmaking.common.testing.kryo.KryoTestClient
import com.github.vatbub.matchmaking.server.logic.ServerContext
import com.github.vatbub.matchmaking.server.logic.testing.dummies.DynamicRequestHandler
import com.github.vatbub.matchmaking.testutils.KotlinTestSuperclassWithExceptionHandlerForMultithreading
import com.github.vatbub.matchmaking.testutils.TestUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.net.InetAddress
import kotlin.random.Random

open class KryoServerTest : KotlinTestSuperclassWithExceptionHandlerForMultithreading<KryoServer>() {
    override fun newObjectUnderTest(): KryoServer {
        if (server == null)
            server = KryoServer(KryoCommon.defaultTcpPort, udpPort)
        return server!!
    }

    private val serverContext = ServerContext()
    private var server: KryoServer? = null
    private var client: KryoTestClient? = null
    private val udpPort: Int?
        get() = if (useUdp()) KryoCommon.defaultTcpPort + 1 else null

    open fun useUdp(): Boolean = false

    @AfterEach
    fun shutServerAndClientDown() {
        client?.client?.stop()
        server?.server?.stop()
        Thread.sleep(2000)
    }

    private fun setServerAndClientUp(clientListener: Listener, tcpPort: Int = KryoCommon.defaultTcpPort, udpPort: Int? = this.udpPort) {
        shutServerAndClientDown()
        server = KryoServer(tcpPort, udpPort, serverContext)
        client = KryoTestClient(clientListener, InetAddress.getLocalHost(), tcpPort, udpPort)
    }

    private fun setServerAndClientForRequestResponseTrafficUp(onUnexpectedObjectReceived: (Any) -> Unit = { Assertions.fail("Unexpected object received: $it") }, tcpPort: Int = KryoCommon.defaultTcpPort, udpPort: Int? = this.udpPort) =
            RequestResponseSetup(tcpPort, udpPort, onUnexpectedObjectReceived)

    inner class RequestResponseSetup(tcpPort: Int = KryoCommon.defaultTcpPort, private val udpPort: Int?, private val onUnexpectedObjectReceived: (Any) -> Unit) {
        private val pendingResponses = mutableMapOf<String, (Response) -> Unit>()

        init {
            setServerAndClientUp(tcpPort = tcpPort, udpPort = udpPort, clientListener = object : Listener() {
                override fun received(connection: Connection?, receivedObject: Any?) {
                    if (receivedObject == null) return
                    if (receivedObject is FrameworkMessage.KeepAlive) return
                    if (receivedObject !is Response) onUnexpectedObjectReceived(receivedObject)
                    receivedObject as Response
                    val handler = pendingResponses.remove(receivedObject.responseTo)
                            ?: return onUnexpectedObjectReceived(receivedObject)
                    handler(receivedObject)
                }
            })
        }

        fun doRequest(request: Request, onResponse: (Response) -> Unit) {
            request.requestId = RequestIdGenerator.getNewId()
            pendingResponses[request.requestId!!] = onResponse
            if (udpPort != null)
                client!!.client.sendUDP(request)
            else
                client!!.client.sendTCP(request)
        }
    }

    @Test
    fun noResponseTest() {
        setServerAndClientForRequestResponseTrafficUp()
                .doRequest(DummyRequest(TestUtils.defaultConnectionId, TestUtils.defaultPassword)) {
                    assertExceptionResponse(InternalServerErrorException(), IllegalStateException("No response generated by server"), 500, it)
                }
    }

    @Test
    fun illegalArgumentExceptionTest() {
        val expectedInnerException = java.lang.IllegalArgumentException("Test exception")
        val handler = DynamicRequestHandler<DummyRequest>({ true }, { false }, { _, _, _ ->
            throw expectedInnerException
        })
        serverContext.messageDispatcher.registerHandler(handler)

        setServerAndClientForRequestResponseTrafficUp()
                .doRequest(DummyRequest(TestUtils.defaultConnectionId, TestUtils.defaultPassword)) {
                    assertExceptionResponse(BadRequestException(), expectedInnerException, 400, it)
                }
    }

    @Test
    fun internalServerErrorExceptionTest() {
        val expectedInnerException = ArrayIndexOutOfBoundsException("Test exception")
        val handler = DynamicRequestHandler<DummyRequest>({ true }, { false }, { _, _, _ ->
            throw expectedInnerException
        })
        serverContext.messageDispatcher.registerHandler(handler)

        setServerAndClientForRequestResponseTrafficUp()
                .doRequest(DummyRequest(TestUtils.defaultConnectionId, TestUtils.defaultPassword)) {
                    assertExceptionResponse(InternalServerErrorException(), expectedInnerException, 500, it)
                }
    }

    private fun assertExceptionResponse(
            expectedOuterException: ServerInteractionException,
            expectedInnerException: Throwable,
            expectedHttpStatusCode: Int,
            actualResponse: Response
    ) {
        assertExceptionResponse(
                expectedOuterException,
                expectedHttpStatusCode,
                """${expectedInnerException.javaClass.name}, ${expectedInnerException.message}""",
                actualResponse
        )
    }

    private fun assertExceptionResponse(
            expectedOuterException: ServerInteractionException,
            expectedHttpStatusCode: Int,
            expectedExceptionMessage: String,
            actualResponse: Response
    ) {
        Assertions.assertEquals(expectedHttpStatusCode, actualResponse.httpStatusCode)
        Assertions.assertEquals(expectedOuterException.className, actualResponse.className)
        Assertions.assertEquals(expectedExceptionMessage, (actualResponse as ServerInteractionException).message)
    }
}

object RequestIdGenerator {
    private val usedIds = mutableListOf<Int>()

    private object Lock

    fun getNewId(): String {
        synchronized(Lock) {
            var id: Int
            do {
                id = Random.nextInt()
            } while (usedIds.contains(id))
            usedIds.add(id)
            return id.toString(16)
        }
    }
}