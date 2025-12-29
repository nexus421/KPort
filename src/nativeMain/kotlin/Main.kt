import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.collections.*
import io.ktor.utils.io.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.coroutines.*
import okio.FileSystem
import platform.posix.time
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds


/**
 * Main entry point of KPort.
 * Initializes the configuration and starts the redirectors for each rule.
 */
fun main() = runBlocking {
    println("Starting KPort...")

    try {
        if (FileSystem.SYSTEM.exists(workingDir).not()) {
            FileSystem.SYSTEM.createDirectories(workingDir)
            println("Created config directory: $workingDir")
            FileSystem.SYSTEM.write(configDir) { writeUtf8(json.encodeToString(Config())) }
            println("Created default config file: $configDir")
        }
    } catch (e: Exception) {
        println("Warning: Could not create config directory $workingDir: ${e.message}")
    }

    val selector = SelectorManager(Dispatchers.IO)
    val rules = config.rules

    if (rules.isEmpty()) {
        println("No rules found in config.json. Please add some rules.")
        exitProcess(1)
    }

    // Launch a redirector coroutine for each rule
    rules.forEach { rule ->
        launch {
            println("Loading rule: ${rule.type} ${rule.portFrom} -> ${rule.ipTo}:${rule.portTo}")
            try {
                when (rule.type) {
                    ConnectionType.TCP -> runTcpRedirector(selector, rule)
                    ConnectionType.UDP -> runUdpRedirector(selector, rule)
                }
            } catch (e: Exception) {
                println("Failed to start redirector for rule $rule: ${e.message}")
            }
        }
    }
}

/**
 * Starts a TCP redirector for the given rule.
 * Listens for incoming connections and handles them.
 */
suspend fun runTcpRedirector(selector: SelectorManager, rule: Rule) = withContext(Dispatchers.IO) {
    try {
        val serverSocket = aSocket(selector).tcp().bind("0.0.0.0", rule.portFrom)
        if (config.debug) println("TCP Listening: Port ${rule.portFrom} -> ${rule.ipTo}:${rule.portTo}")

        while (isActive) {
            val clientSocket = serverSocket.accept()
            launch {
                if (config.debug) println("Accepted TCP connection from ${clientSocket.remoteAddress}")
                handleConnection(selector, clientSocket, rule)
            }
        }
    } catch (e: Exception) {
        if (e !is kotlinx.coroutines.CancellationException) {
            val errorMessage =
                if (rule.portFrom < 1024 && (e.message?.contains("Permission denied") == true || e.message?.contains("error 13") == true)) {
                    "Error: Permission denied binding to port ${rule.portFrom}. Ports below 1024 require root privileges or CAP_NET_BIND_SERVICE."
                } else {
                    "Error in TCP Redirector on port ${rule.portFrom}: ${e.message}"
                }
            println(errorMessage)
        }
    }
}

/**
 * Handles a single TCP connection by piping data between the client and the target.
 */
suspend fun handleConnection(selector: SelectorManager, clientSocket: Socket, rule: Rule) =
    withContext(Dispatchers.IO) {
        clientSocket.use { client ->
            try {
                val targetSocket = aSocket(selector).tcp().connect(rule.ipTo, rule.portTo)
                targetSocket.use { target ->
                    val clientRead = client.openReadChannel()
                    val clientWrite = client.openWriteChannel()
                    val targetRead = target.openReadChannel()
                    val targetWrite = target.openWriteChannel()

                    // Pipe client data to target
                    val clientToTarget = launch {
                        try {
                            clientRead.copyAndClose(targetWrite)
                        } catch (e: Exception) {
                            if (config.debug) println("TCP clientToTarget Error: ${e.message}")
                        }
                    }

                    // Pipe target data to client
                    val targetToClient = launch {
                        try {
                            targetRead.copyAndClose(clientWrite)
                        } catch (e: Exception) {
                            if (config.debug) println("TCP targetToClient Error: ${e.message}")
                        }
                    }

                    joinAll(clientToTarget, targetToClient)
                }
            } catch (e: Exception) {
                if (config.debug) println("Error connecting to target ${rule.ipTo}:${rule.portTo}: ${e.message}")
            }
        }
    }

/**
 * Returns current time in seconds since epoch.
 */
@OptIn(ExperimentalForeignApi::class)
private fun currentTimeSeconds(): Long = time(null).convert()

/**
 * Starts a UDP redirector for the given rule.
 * Manages sessions for each client address to track where responses should be sent.
 */
suspend fun runUdpRedirector(selector: SelectorManager, rule: Rule) = withContext(Dispatchers.IO) {
    val serverSocket = aSocket(selector).udp().bind(InetSocketAddress("0.0.0.0", rule.portFrom))
    val sessions = ConcurrentMap<SocketAddress, UdpSession>()

    if (config.debug) println("UDP Listening: ${rule.portFrom} -> ${rule.ipTo}:${rule.portTo}")

    // Cleanup job for inactive sessions
    launch {
        while (isActive) {
            delay(30.seconds)
            val now = currentTimeSeconds()
            sessions.entries.forEach { (address, session) ->
                if (now - session.lastSeen > 60) {
                    if (config.debug) println("UDP Cleanup: Session for $address expired.")
                    session.job.cancel()
                    session.socket.close()
                    sessions.remove(address)
                }
            }
        }
    }

    try {
        val targetAddress = InetSocketAddress(rule.ipTo, rule.portTo)
        while (isActive) {
            val datagram = serverSocket.receive()
            val clientAddress = datagram.address

            val session = sessions.getOrPut(clientAddress) {
                if (config.debug) println("UDP: New session for $clientAddress")
                val targetSocket = aSocket(selector).udp().connect(targetAddress)

                val job = launch {
                    try {
                        while (isActive) {
                            val response = targetSocket.receive()
                            serverSocket.send(Datagram(response.packet, clientAddress))
                        }
                    } catch (e: Exception) {
                        if (e !is kotlinx.coroutines.CancellationException && config.debug) {
                            println("UDP Session Job Error ($clientAddress): ${e.message}")
                        }
                    } finally {
                        targetSocket.close()
                        sessions.remove(clientAddress)
                    }
                }
                UdpSession(targetSocket, currentTimeSeconds(), job)
            }

            session.lastSeen = currentTimeSeconds()
            session.socket.send(Datagram(datagram.packet, targetAddress))
        }
    } catch (e: Exception) {
        if (e !is kotlinx.coroutines.CancellationException) {
            val errorMessage =
                if (rule.portFrom < 1024 && (e.message?.contains("Permission denied") == true || e.message?.contains("error 13") == true)) {
                    "Error: Permission denied binding to UDP port ${rule.portFrom}. Ports below 1024 require root privileges or CAP_NET_BIND_SERVICE."
                } else {
                    "Error UDP Redirector (${rule.portFrom}): ${e.message}"
                }
            println(errorMessage)
        }
    } finally {
        serverSocket.close()
        sessions.values.forEach {
            it.job.cancel()
            it.socket.close()
        }
        sessions.clear()
    }
}