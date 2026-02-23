package org.jetbrains.kastle.analytics.fus.event

import com.jetbrains.fus.reporting.schema.VarargEventId
import org.jetbrains.kastle.PackId
import org.jetbrains.kastle.VariableId
import org.jetbrains.kastle.analytics.GenerationEvent
import org.jetbrains.kastle.analytics.fus.ClientInfo
import org.jetbrains.kastle.analytics.fus.FusAnalyticsRepository
import org.jetbrains.kastle.analytics.fus.event.FusSchema.Browser
import org.jetbrains.kastle.analytics.fus.event.FusSchema.ClientType
import org.jetbrains.kastle.analytics.fus.event.FusSchema.Dependency
import org.jetbrains.kastle.analytics.fus.event.FusSchema.PLUGIN_GENERATED_EVENT
import org.jetbrains.kastle.analytics.fus.event.FusSchema.PluginGeneratedFields
import org.jetbrains.kastle.analytics.fus.event.FusSchema.ProjectGeneratedFields
import org.jetbrains.kastle.analytics.fus.event.FusSchema.THEME_GENERATED_EVENT
import org.jetbrains.kastle.analytics.fus.event.FusSchema.ThemeGeneratedFields

private const val PLUGIN_PACK_GROUP = "org.jetbrains.intellij.platform"
private val pluginPackId = PackId(PLUGIN_PACK_GROUP, "plugin")
private val themePackId = PackId(PLUGIN_PACK_GROUP, "theme")

/**
 * Interface for converting [GenerationEvent] to FUS event data.
 */
interface EventConverter<D : ProjectGeneratedFields> {
    fun getFusEventId(): VarargEventId<D>
    fun getDataFiller(event: GenerationEvent): D.() -> Unit

    companion object {
        fun getConverter(event: GenerationEvent): EventConverter<out ProjectGeneratedFields> {
            return when {
                event.packs.contains(pluginPackId) -> PluginEventConverter()
                event.packs.contains(themePackId) -> ThemeEventConverter()
                else -> throw IllegalArgumentException("Unknown plugin pack type: ${event.packs.joinToString()}")
            }
        }
    }
}

class PluginEventConverter : EventConverter<PluginGeneratedFields> {
    override fun getFusEventId(): VarargEventId<PluginGeneratedFields> = PLUGIN_GENERATED_EVENT

    override fun getDataFiller(event: GenerationEvent): PluginGeneratedFields.() -> Unit {
        return {
            fillClientFields(event)
            sampleCode = extractSampleCode(event)
            dependencies = extractDependencies(event)
        }
    }

    private fun extractSampleCode(event: GenerationEvent): Boolean {
        val sampleCodeVariableId = VariableId(pluginPackId, "addSampleCode")
        return event.properties[sampleCodeVariableId]?.toBoolean() ?: false
    }

    private fun extractDependencies(event: GenerationEvent): List<String> {
        return event.packs
            // we could do simple parsing here, but let's keep the map for clarity
            // and get the name through enum to not leak unsupported values
            .mapNotNull { packIdToDependencyEnum[it]?.name }
    }

    companion object {
        private const val DEPENDENCIES_PACK_GROUP = "org.jetbrains.intellij.platform.dependencies"
        private const val PLUGINS_PACK_GROUP = "org.jetbrains.intellij.platform.plugins"

        private val packIdToDependencyEnum = mapOf(
            PackId(DEPENDENCIES_PACK_GROUP, "compose") to Dependency.compose,
            PackId(DEPENDENCIES_PACK_GROUP, "lsp") to Dependency.lsp,
            PackId(PLUGINS_PACK_GROUP, "database") to Dependency.database,
            PackId(PLUGINS_PACK_GROUP, "go") to Dependency.go,
            PackId(PLUGINS_PACK_GROUP, "java") to Dependency.java,
            PackId(PLUGINS_PACK_GROUP, "javascript") to Dependency.javascript,
            PackId(PLUGINS_PACK_GROUP, "json") to Dependency.json,
            PackId(PLUGINS_PACK_GROUP, "kotlin") to Dependency.kotlin,
            PackId(PLUGINS_PACK_GROUP, "markdown") to Dependency.markdown,
            PackId(PLUGINS_PACK_GROUP, "php") to Dependency.php,
            PackId(PLUGINS_PACK_GROUP, "properties") to Dependency.properties,
            PackId(PLUGINS_PACK_GROUP, "python") to Dependency.python,
            PackId(PLUGINS_PACK_GROUP, "ruby") to Dependency.ruby,
            PackId(PLUGINS_PACK_GROUP, "rust") to Dependency.rust,
            PackId(PLUGINS_PACK_GROUP, "xml") to Dependency.xml,
            PackId(PLUGINS_PACK_GROUP, "yaml") to Dependency.yaml,
        )
    }
}

class ThemeEventConverter : EventConverter<ThemeGeneratedFields> {
    override fun getFusEventId(): VarargEventId<ThemeGeneratedFields> = THEME_GENERATED_EVENT

    override fun getDataFiller(event: GenerationEvent): ThemeGeneratedFields.() -> Unit {
        return {
            fillClientFields(event)
        }
    }
}

private fun ProjectGeneratedFields.fillClientFields(event: GenerationEvent) {
    val clientInfo = event.additionalParameters[FusAnalyticsRepository.USER_AGENT_PARAMETER_NAME]
        ?.let(ClientInfo::parse)
        ?: ClientInfo.Unknown
    clientType = clientInfo.clientType.toFusClientType()
    clientVersion = clientInfo.clientVersion?.value
    browser = (clientInfo.clientType as? ClientInfo.ClientType.Web)?.toFusBrowser()
}

private fun ClientInfo.ClientType.toFusClientType(): ClientType {
    return when (this) {
        ClientInfo.ClientType.IDEA -> ClientType.IDEA
        ClientInfo.ClientType.AS -> ClientType.AS
        is ClientInfo.ClientType.Web -> ClientType.Web
        else -> ClientType.Other
    }
}

private fun ClientInfo.ClientType.Web.toFusBrowser(): Browser {
    return when (this.browser) {
        ClientInfo.Browser.Safari -> Browser.Safari
        ClientInfo.Browser.Chrome -> Browser.Chrome
        ClientInfo.Browser.Firefox -> Browser.Firefox
        ClientInfo.Browser.Opera -> Browser.Opera
        ClientInfo.Browser.Edge -> Browser.Edge
        ClientInfo.Browser.Other -> Browser.Other
    }
}
