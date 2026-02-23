package org.jetbrains.kastle.analytics.fus

import org.jetbrains.kastle.analytics.fus.ClientInfo.ClientType.Other

/**
 * The `User-Agent` header wrapper extracting the information about:
 * - client type
 * - client version
 * - browser type (if the client type is Web)
 */
class ClientInfo private constructor(
    val clientType: ClientType,
    val clientVersion: ClientVersion? = null,
) {
    companion object {
        val VERSION_EXTENDED_REGEX_V2 = Regex("((\\d+\\.?)*\\d+)|(((\\d+\\.?)*\\d+)-(beta|eap|rc)-\\d+)")
        private val productRegex = Regex("""(?<client>\w+(?: \w+)*?)/(?<version>[\w.-]+)""")

        val Unknown = ClientInfo(Other)

        fun parse(userAgent: String): ClientInfo? {
            return productRegex.findAll(userAgent).mapNotNull { match ->
                val client = match.groups["client"]?.value?.let(ClientType::forUserAgentName) ?: return@mapNotNull null
                val version = match.groups["version"]?.value?.let(ClientVersion::parse)
                ClientInfo(client, version)
            }.minByOrNull { it.clientType }
        }
    }

    @JvmInline
    value class ClientVersion private constructor(val value: String) {
        companion object {
            fun parse(value: String): ClientVersion? {
                return if (VERSION_EXTENDED_REGEX_V2.matches(value)) ClientVersion(value) else null
            }
        }
    }

    sealed class ClientType : Comparable<ClientType> {
        companion object {
            private val browserNames = Browser.entries.flatMap { it.aliases + it.name }

            fun forUserAgentName(name: String): ClientType? = when (name) {
                "IntelliJ IDEA" -> IDEA
                "Android Studio" -> AS
                "CLI" -> CLI
                in browserNames -> Web(Browser.byName(name))
                else -> null
            }
        }

        override fun compareTo(other: ClientType): Int = when (this) {
            AS, CLI, IDEA -> -1
            Other -> 1
            is Web -> when (other) {
                is Web -> other.browser.ordinal - browser.ordinal
                else -> 1
            }
        }

        data object IDEA : ClientType()
        data object AS : ClientType()
        data object CLI : ClientType()
        data class Web(val browser: Browser) : ClientType()
        data object Other : ClientType()
    }

    /**
     * Major browsers for analytics, including aliases when looking through user agent strings.
     *
     * Note: the order of the entries maps to the precedence; this is important because strings like "Chrome" appear in
     *       other browsers like Edge and Opera.
     */
    enum class Browser(vararg aliases: String) {
        Safari,
        Chrome,
        Firefox,
        Opera("OPR"),
        Edge("Edg"),
        Other;

        val aliases: Set<String> = aliases.toSet()

        companion object {
            fun byName(name: String): Browser {
                return entries.find { name == it.name || name in it.aliases } ?: Other
            }
        }
    }
}
