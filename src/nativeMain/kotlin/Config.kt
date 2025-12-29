import kotlinx.serialization.Serializable

/**
 * Represents the main configuration of the application.
 * @property debug If true, detailed debug information will be printed to the console.
 * @property rules A list of forwarding rules to be applied.
 */
@Serializable
data class Config(val debug: Boolean = false, val rules: List<Rule> = emptyList())