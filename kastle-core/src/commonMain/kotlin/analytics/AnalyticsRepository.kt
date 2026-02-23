package org.jetbrains.kastle.analytics

/**
 * Repository interface for recording project generation analytics events.
 *
 * Implementations can store events in various backends (FUS, DynamoDB, PostgreSQL, etc.).
 * The default implementation is [NoOpAnalyticsRepository] which discards all events.
 *
 * Implementations must provide a constructor with a constructor taking a single parameter of type
 * [kotlinx.coroutines.CoroutineScope]. The passed scope is the application scope and can be used to launch coroutines
 * for asynchronous operations or background tasks.
 */
interface AnalyticsRepository : AutoCloseable {
    /**
     * Returns the header and cookie mappings used by this implementation.
     */
    val requestMappings: RequestMappings
        get() = RequestMappings.EMPTY

    /**
     * Records a generation event.
     * Implementations should be non-blocking and handle errors gracefully.
     */
    suspend fun record(event: GenerationEvent)
}

/**
 * Declares header and cookie mappings used to fetch required data by an analytics implementation from a request.
 *
 * @property headerMappings Map of HTTP header name to parameter name
 * @property cookieMappings Map of cookie name to parameter name
 */
data class RequestMappings(
    val headerMappings: Map<String, String> = emptyMap(),
    val cookieMappings: Map<String, String> = emptyMap()
) {
    companion object {
        val EMPTY = RequestMappings()
    }
}

/**
 * Default no-op implementation that discards all events.
 * Used when analytics recording is disabled or no implementation is configured.
 */
object NoOpAnalyticsRepository : AnalyticsRepository {
    override suspend fun record(event: GenerationEvent) = Unit
    override fun close() {}
}
