package org.jetbrains.kastle.analytics.fus

import com.typesafe.config.ConfigFactory
import org.jetbrains.kastle.logging.ConsoleLogger

/**
 * Type-safe configuration wrapper for [FusAnalyticsRepository].
 *
 * @property productName Product name for FUS reporting
 * @property productCode Product code for FUS reporting
 * @property recorderCode Recorder code for FUS reporting
 * @property baselineVersion Baseline version for FUS reporting
 * @property buildNumber Build number/version for FUS reporting
 * @property recorderVersion Recorder version for FUS reporting (default: 1)
 * @property test Set to true for test mode (default: false)
 * @property flushEventsDelaySeconds Delay in seconds between event flushes (default: 10)
 */
data class FusAnalyticsConfig(
    val productName: String,
    val productCode: String,
    val recorderCode: String,
    val baselineVersion: Int,
    val buildNumber: String,
    val recorderVersion: Int = 1,
    val test: Boolean = false,
    val flushEventsDelaySeconds: Long = 10
) {
    companion object {
        private val log = ConsoleLogger()
        private const val CONFIG_FILE = "fus-analytics.conf"

        /**
         * Loads FUS analytics configuration from the bundled resource file.
         *
         * The configuration is loaded from `fus-analytics.conf` in the classpath.
         * Values can be overridden via environment variables as defined in the config file.
         *
         * @return FusAnalyticsConfig instance with loaded values
         * @throws IllegalArgumentException if required parameters are missing or invalid
         */
        fun load(): FusAnalyticsConfig {
            val configUrl = FusAnalyticsConfig::class.java.classLoader.getResource(CONFIG_FILE)
                ?: throw IllegalArgumentException("Config file not found: $CONFIG_FILE")
            val fusConfig = ConfigFactory.parseURL(configUrl)
                .resolve() // populate environment variables
                .getConfig("fus")

            return FusAnalyticsConfig(
                productName = fusConfig.getString("productName"),
                productCode = fusConfig.getString("productCode"),
                recorderCode = fusConfig.getString("recorderCode"),
                baselineVersion = fusConfig.getString("baselineVersion").toInt(),
                buildNumber = fusConfig.getString("buildNumber"),
                recorderVersion = fusConfig.getString("recorderVersion").toIntOrNull() ?: 1,
                test = fusConfig.getBoolean("test"),
                flushEventsDelaySeconds = fusConfig.getLong("flushEventsDelaySeconds")
            ).also {
                log.info { "Loaded FUS analytics config: $it" }
            }
        }
    }
}
