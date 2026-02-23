package org.jetbrains.kastle.analytics.fus.event

import com.jetbrains.fus.reporting.schema.EventFields.Boolean
import com.jetbrains.fus.reporting.schema.EventFields.Enum
import com.jetbrains.fus.reporting.schema.EventFields.StringList
import com.jetbrains.fus.reporting.schema.EventFields.StringValidatedByInlineRegexp
import com.jetbrains.fus.reporting.schema.EventLogGroup
import com.jetbrains.fus.reporting.schema.FieldListDescription
import com.jetbrains.fus.reporting.schema.VarargEventId

object FusSchema {

    /**
     * EventLogGroup for tracking usage of the IntelliJ Platform Plugin Generator.
     * Reports events when users generate plugin projects through the web interface.
     */
    val PLUGIN_GENERATOR_GROUP = EventLogGroup(
        "intellij.plugin.generator",
        1,
        EventLogGroup.Type.COUNTER,
        "Reports the usages of the IntelliJ Platform Plugin Generator available at https://plugins.jetbrains.com/generator"
    )

    val PLUGIN_GENERATED_EVENT: VarargEventId<PluginGeneratedFields> = PLUGIN_GENERATOR_GROUP.registerEvent(
        "plugin.generated",
        ::PluginGeneratedFields,
        "Recorded when a user generates a regular plugin project"
    )

    abstract class ProjectGeneratedFields : FieldListDescription() {
        var clientType by field(
            Enum("client_type", ClientType::class.java, "The client used in the request")
        )
        var clientVersion by field(
            StringValidatedByInlineRegexp("client_version", VERSION_REGEXP, "The client version used in the request")
        )
        var browser by field(
            Enum("browser", Browser::class.java, "The browser used in the request")
        )
    }

    class PluginGeneratedFields : ProjectGeneratedFields() {
        var sampleCode by field(
            Boolean("sample_code", "Including sample code enabled")
        )
        var dependencies by field(
            StringList("dependencies", Dependency.entries.map { it.name }, "List of dependencies")
        )
    }

    val THEME_GENERATED_EVENT: VarargEventId<ThemeGeneratedFields> = PLUGIN_GENERATOR_GROUP.registerEvent(
        "theme.generated",
        ::ThemeGeneratedFields,
        "Recorded when a user generates a theme plugin project"
    )

    class ThemeGeneratedFields : ProjectGeneratedFields()

    /**
     * Validation regexp for IDE versions.
     * Matches patterns like: "2023.1", "2023.1.1", "2023.1 EAP"
     */
    private const val VERSION_REGEXP = """^\d+\.\d+(?:\.\d+)*(\s\w+)?$"""

    enum class Browser {
        Chrome,
        Firefox,
        Safari,
        Opera,
        Edge,
        Other
    }

    enum class ClientType {
        IDEA,
        AS,
        Web,
        Other
    }

    @Suppress("EnumEntryName") // names must be lower-case
    enum class Dependency {
        compose,
        lsp,
        database,
        go,
        java,
        javascript,
        json,
        kotlin,
        markdown,
        php,
        properties,
        python,
        ruby,
        rust,
        xml,
        yaml
    }

}
