package org.jetbrains.kastle.server

import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.server.plugins.di.annotations.*
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kastle.ProjectDescriptor
import org.jetbrains.kastle.analytics.AnalyticsRepository
import org.jetbrains.kastle.analytics.GenerationEvent
import org.jetbrains.kastle.analytics.NoOpAnalyticsRepository
import org.jetbrains.kastle.analytics.RequestMappings

/**
 * Configures analytics tracking for project generation.
 *
 * Analytics is disabled by default. To enable it, set the following configuration:
 * - `analytics.enabled`: Set to `true` to enable analytics
 * - `analytics.implementation`: Fully qualified class name of [AnalyticsRepository] implementation
 *
 * The implementation class must have a constructor that accepts a [CoroutineScope] parameter.
 */
fun Application.configureAnalytics(
    @Property("analytics.enabled") enabled: Boolean = false,
    @Property("analytics.implementation") implementation: String? = null,
) {
    val log = environment.log

    val repository: AnalyticsRepository = if (!enabled || implementation.isNullOrBlank()) {
        log.info("Analytics disabled")
        NoOpAnalyticsRepository
    } else {
        try {
            log.info("Loading analytics implementation: $implementation")
            @Suppress("UNCHECKED_CAST")
            Class.forName(implementation)
                .getDeclaredConstructor(CoroutineScope::class.java)
                .newInstance(this) as AnalyticsRepository
        } catch (e: Exception) {
            log.error("Failed to load analytics implementation: $implementation", e)
            NoOpAnalyticsRepository
        }
    }

    dependencies {
        provide<AnalyticsRepository> { repository }
    }
}

suspend fun ApplicationCall.recordAnalyticsEvent(
    analytics: AnalyticsRepository,
    descriptor: ProjectDescriptor
) {
    val additionalParameters = buildAdditionalParameters(analytics.requestMappings)
    val generationEvent = GenerationEvent.from(descriptor, additionalParameters)
    analytics.record(generationEvent)
    application.log.info("Generated project: $generationEvent")
}

/**
 * Builds an additional parameters map from HTTP request headers based on analytics repository parameter requirements.
 *
 * @param requestMappings Parameter mappings to extract information from headers
 * @return Map of parameter names to their values
 */
private fun ApplicationCall.buildAdditionalParameters(requestMappings: RequestMappings): Map<String, String> {
    val parameters = mutableMapOf<String, String>()

    requestMappings.headerMappings.forEach { (headerName, paramName) ->
        request.headers[headerName]?.let { value ->
            parameters[paramName] = value
        }
    }

    return parameters
}
