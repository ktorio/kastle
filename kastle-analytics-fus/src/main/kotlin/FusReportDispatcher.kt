package org.jetbrains.kastle.analytics.fus

import com.jetbrains.fus.reporting.FusHttpClient
import com.jetbrains.fus.reporting.FusJsonSerializer
import com.jetbrains.fus.reporting.FusReportDispatcher
import com.jetbrains.fus.reporting.RemoteConfig
import com.jetbrains.fus.reporting.model.lion4.FusReport
import com.jetbrains.fus.reporting.model.lion4.LogEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.flow.consumeAsFlow
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

// FIXME: reuse lib dispatcher when ready
internal class FusReportDispatcher(
    private val remoteConfig: RemoteConfig,
    private val httpClient: FusHttpClient,
    private val jsonSerializer: FusJsonSerializer,
    private val coroutineScope: CoroutineScope,
) : FusReportDispatcher, AutoCloseable {

    private val sendQueue = Channel<PostData>(capacity = UNLIMITED)

    private val senderJob: Job = coroutineScope.launch {
        sendQueue.consumeAsFlow().collect { (url, data) ->
            runCatching {
                withContext(Dispatchers.IO) {
                    retryUntil {
                        val response = httpClient.post(url, data)
                        response.statusCode.isOk.also { ok ->
                            if (!ok) {
                                log.warn("Failed to send report $data to $url. Status code: ${response.statusCode}, body: ${response.body}")
                            }
                        }
                    }
                }
            }.onFailure {
                if (it is CancellationException) throw it
                log.warn("Failed to send report $data to $url", it)
            }
        }
    }

    private var batch = ArrayList<LogEvent>()
    private val batchLock = Any()

    override fun writeEvent(event: LogEvent) = synchronized(batchLock) {
        batch.addLast(event)
    }

    override fun getReport(): FusReport? = synchronized(batchLock) {
        if (batch.isEmpty()) return null
        FusReport(batch).also { batch = ArrayList() }
    }

    override fun dispatchReport(report: FusReport) {
        val reportJson = jsonSerializer.toJson(report)
        val sendUrl = remoteConfig.getSendUrl()
        sendQueue.trySend(PostData(sendUrl, reportJson)).onFailure {
            log.warn("Failed to queue report for sending: $it")
        }
    }

    override fun close() {
        senderJob.cancel()
        sendQueue.close()
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(FusReportDispatcher::class.java)
    }
}

private const val MAX_RETRIES = 48

private suspend inline fun retryUntil(body: suspend () -> Boolean) {
    var retryCount = 0
    var delay = 100.milliseconds
    while (currentCoroutineContext().isActive) {
        try {
            val success = body()
            if (success) return

            if (retryCount >= MAX_RETRIES) throw IllegalStateException("Max retries exceeded with success = false")
        } catch (cause: Throwable) {
            if (cause is CancellationException) throw cause
            if (retryCount >= MAX_RETRIES) throw cause
        }
        retryCount += 1
        delay(delay)
        delay = minOf(delay * 2, 30.minutes)
    }
}

private data class PostData(val url: String, val data: String)

private inline val Int.isOk: Boolean
    get() = this in 200..<300
