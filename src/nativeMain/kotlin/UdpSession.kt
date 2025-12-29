import io.ktor.network.sockets.ConnectedDatagramSocket
import kotlinx.coroutines.Job

/**
 * Holds information about an active UDP session.
 * @property socket The socket connected to the target address.
 * @property lastSeen Timestamp (seconds) of the last packet received from the client.
 * @property job The coroutine job handling responses from the target back to the client.
 */
class UdpSession(
    val socket: ConnectedDatagramSocket,
    var lastSeen: Long,
    val job: Job
)