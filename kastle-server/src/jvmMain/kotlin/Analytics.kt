package org.jetbrains.kastle.server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.server.plugins.di.annotations.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kastle.ProjectDescriptor
import org.jetbrains.kastle.analytics.AnalyticsRepository
import org.jetbrains.kastle.analytics.GenerationEvent
import org.jetbrains.kastle.analytics.NoOpAnalyticsRepository
import org.jetbrains.kastle.analytics.RequestMappings
import org.jetbrains.kastle.utils.generateRandomHash

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

const val USER_ID_COOKIE_NAME = "kastle_user_id"

/**
 * Sets or updates the [user ID cookie][USER_ID_COOKIE_NAME] with 400-day expiration.
 */
fun RoutingContext.initUserIdCookie() {
    val existingCookieValue = call.request.cookies[USER_ID_COOKIE_NAME]
    val cookieValue = existingCookieValue ?: generateRandomHash()
    val maxAgeInSeconds = 400 * 24 * 60 * 60 // 400 days
    call.response.cookies.append(
        Cookie(
            name = USER_ID_COOKIE_NAME,
            value = cookieValue,
            maxAge = maxAgeInSeconds,
            path = "/"
        )
    )
}

suspend fun ApplicationCall.recordAnalyticsEvent(
    analytics: AnalyticsRepository,
    descriptor: ProjectDescriptor
) {
    val additionalParameters = buildAdditionalParameters(analytics.requestMappings)
    val generationEvent = GenerationEvent.from(descriptor, additionalParameters)
    analytics.record(generationEvent)
    application.log.info("Generated project: $descriptor")
}

/**
 * Builds an additional parameters map from an HTTP request based on analytics repository parameter requirements.
 *
 * Cookies may override headers if they map to the same parameter name.
 *
 * @param requestMappings Parameter mappings to extract information from headers and cookies
 * @return Map of parameter names to their values
 */
private fun ApplicationCall.buildAdditionalParameters(requestMappings: RequestMappings): Map<String, String> {
    val parameters = mutableMapOf<String, String>()

    requestMappings.headerMappings.forEach { (headerName, paramName) ->
        request.headers[headerName]?.let { value ->
            parameters[paramName] = value
        }
    }

    // cookies may override headers
    requestMappings.cookieMappings.forEach { (cookieName, paramName) ->
        request.cookies[cookieName]?.let { value ->
            parameters[paramName] = value
        }
    }

    return parameters
}
