package org.jetbrains.kastle.analytics.fus

import com.jetbrains.fus.reporting.*
import com.jetbrains.fus.reporting.defaults.*
import com.jetbrains.fus.reporting.jvm.JvmFileStorage
import com.jetbrains.fus.reporting.jvm.JvmHttpClient
import com.jetbrains.fus.reporting.jvm.JvmLoggerFactory
import com.jetbrains.fus.reporting.lion4.FeatureUsageLogger
import com.jetbrains.fus.reporting.model.lion4.FusRecorder
import com.jetbrains.fus.reporting.model.userIdToBucket
import com.jetbrains.fus.reporting.serialization.FusKotlinSerializer
import kotlinx.coroutines.*
import org.jetbrains.kastle.analytics.AnalyticsRepository
import org.jetbrains.kastle.analytics.GenerationEvent
import org.jetbrains.kastle.analytics.RequestMappings
import org.jetbrains.kastle.analytics.fus.event.EventConverter
import org.jetbrains.kastle.analytics.fus.event.FusSchema.PLUGIN_GENERATOR_GROUP
import org.jetbrains.kastle.analytics.fus.event.FusSchema.ProjectGeneratedFields
import org.jetbrains.kastle.utils.generateRandomHash
import java.security.SecureRandom
import java.util.*
import javax.net.ssl.SSLContext
import kotlin.io.path.createTempDirectory
import kotlin.time.Duration.Companion.seconds
import com.jetbrains.fus.reporting.FusClient as LibFusClient

class FusAnalyticsRepository(
    private val applicationScope: CoroutineScope
) : AnalyticsRepository {

    private val config = FusAnalyticsConfig.load()

    private val libFusClient: LibFusClient = configureLibFusClient()

    override val requestMappings: RequestMappings = RequestMappings(
        headerMappings = mapOf(
            "User-Agent" to USER_AGENT_PARAMETER_NAME,
            "X-Machine-ID" to MACHINE_ID_PARAMETER_NAME
        ),
        cookieMappings = mapOf(
            "kastle_client_id" to MACHINE_ID_PARAMETER_NAME
        )
    )

    override suspend fun record(event: GenerationEvent) {
        val machineId = event.additionalParameters[MACHINE_ID_PARAMETER_NAME] ?: generateRandomHash()

        val fusLogger = logger(
            machineId = machineId,
            session = UUID.randomUUID().toString(), // we track one event per session
            bucket = userIdToBucket(machineId)
        )
        fusLogger.logBaseline(PLUGIN_GENERATOR_GROUP)
        fusLogger.logEvent(EventConverter.getConverter(event), event)
    }

    private fun <D : ProjectGeneratedFields> FeatureUsageLogger.logEvent(
        converter: EventConverter<D>,
        event: GenerationEvent
    ) {
        logVararg(converter.getFusEventId(), converter.getDataFiller(event))
    }

    private fun logger(machineId: String, session: String, bucket: Int): FeatureUsageLogger {
        return FeatureUsageLogger(
            recorder = FusRecorder(libFusClient.config.recorderCode, config.recorderVersion),
            productCode = libFusClient.config.productCode,
            ids = mapOf("machine_id" to machineId),
            internal = false,
            build = libFusClient.config.productVersion,
            session = session,
            bucket = bucket,
            writer = libFusClient,
        )
    }

    override fun close() {
        libFusClient.close()
    }

    private fun configureLibFusClient(): LibFusClient {
        return fusClient {
            config {
                productName = this@FusAnalyticsRepository.config.productName
                productCode = this@FusAnalyticsRepository.config.productCode
                recorderCode = this@FusAnalyticsRepository.config.recorderCode
                productVersion = this@FusAnalyticsRepository.config.buildNumber
                baselineVersion = this@FusAnalyticsRepository.config.baselineVersion
                reduceInitialMetadataUpdateDelay = true
                regionCode = RegionCode.ALL
                isTest = this@FusAnalyticsRepository.config.test
            }

            components {
                loggerFactory { JvmLoggerFactory() }
                jvmHttpClient()
                jvmFileStorage()
                jsonSerializer { FusKotlinSerializer() }
                defaultRemoteConfig()
                defaultMetadataStorage()
                reportAnonymizer { _, _ -> NoOpAnonymizer() }
                reportValidator(::DefaultReportValidator)
                reportDispatcher { _, remoteConfig, httpClient, jsonSerializer, _ ->
                    FusReportDispatcher(remoteConfig, httpClient, jsonSerializer, applicationScope)
                }
            }
        }.also { client ->
            applicationScope.launch { client.scheduleMetadataUpdate(this) }
            applicationScope.launch {
                do {
                    delay(config.flushEventsDelaySeconds.seconds)
                    client.flushEvents()
                } while (currentCoroutineContext().isActive)
            }
        }
    }

    private fun FusClientComponents.Builder.jvmHttpClient() = httpClient { config ->
        JvmHttpClient(
            sslContextProvider = {
                SSLContext.getInstance("TLS").apply {
                    init(null, null, SecureRandom())
                }
            },
            userAgent = "${config.productName}/${config.productVersion}"
        )
    }

    private fun FusClientComponents.Builder.jvmFileStorage() = fileStorage {
        JvmFileStorage(createTempDirectory("fus-client"))
    }

    private fun FusClientComponents.Builder.defaultRemoteConfig() = remoteConfig { config, httpClient, jsonSerializer, _ ->
        DefaultRemoteConfig(config, jsonSerializer, httpClient)
    }

    private fun FusClientComponents.Builder.defaultMetadataStorage() =
        metadataStorage { config, messageBus, loggerFactory, remoteConfig, httpClient, fileStorage, jsonSerializer, bundledFileStorage ->
            DefaultMetadataStorage(
                config,
                messageBus,
                loggerFactory,
                remoteConfig,
                httpClient,
                jsonSerializer,
                fileStorage,
                bundledFileStorage,
                metadataUpdateDelay = MetadataUpdateDelay.SHORT,
                buildParser = DEFAULT_BUILD_PARSER,
            )
        }

    companion object {
        const val MACHINE_ID_PARAMETER_NAME = "machineId"
        const val USER_AGENT_PARAMETER_NAME = "userAgent"
    }
}
