/**
 * @target slot://io.ktor/server-content-negotiation/install
 */
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*

fun ContentNegotiationConfig.install() {
    json()
}
