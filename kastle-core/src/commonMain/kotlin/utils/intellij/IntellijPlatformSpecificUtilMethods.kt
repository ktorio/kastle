package org.jetbrains.kastle.utils.intellij

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
object IntellijPlatformSpecificUtilMethods {

    val invalidPackageNameSymbolPattern = Regex("^\\d|[^a-zA-Z\\d_.]")

    private val methods = mapOf<String, (List<Any?>) -> Any?>(
        "uuid" to { _ -> Uuid.random().toString() },
        "sanitizePackageName" to { args ->
            val arg = args.singleOrNull()
                ?: throw IllegalArgumentException("sanitizePackageName requires one argument")
            // keep in sync with org.jetbrains.idea.devkit.module.webstarter.IdePluginModuleWebBasedBuilder#sanitizeThemeFilename
            arg.toString().replace("-", "")
                .replace(invalidPackageNameSymbolPattern, "_")
                .replace(Regex("\\s"), "")
        },
        "toPluginModuleName" to { args ->
            val arg = args.singleOrNull()
                ?: throw IllegalArgumentException("pluginModuleName requires one argument")
            arg.toString().replace("-", ".")
                .replace(invalidPackageNameSymbolPattern, ".")
                .replace(Regex("\\s"), "")
                .removePrefix("intellij.") // intellij prefix is restricted for JetBrains only
        }
    )

    fun supportsStaticMethod(methodName: String): Boolean {
        return methodName in methods
    }

    fun evaluateStaticMethod(methodName: String, args: List<Any?>): Any? {
        return methods[methodName]?.invoke(args)
    }

}
