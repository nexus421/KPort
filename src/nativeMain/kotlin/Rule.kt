import kotlinx.serialization.Serializable

/**
 * Defines a single forwarding rule.
 * @property portFrom Local port to listen on.
 * @property portTo Remote port to forward to.
 * @property ipTo Remote IP address to forward to.
 * @property type Connection type (TCP or UDP).
 */
@Serializable
data class Rule(
    val portFrom: Int,
    val portTo: Int,
    val ipTo: String,
    val type: ConnectionType = ConnectionType.TCP
)