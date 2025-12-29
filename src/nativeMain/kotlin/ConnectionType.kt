import kotlinx.serialization.Serializable

/**
 * Supported connection types for forwarding.
 */
@Serializable
enum class ConnectionType{
    TCP, UDP
}