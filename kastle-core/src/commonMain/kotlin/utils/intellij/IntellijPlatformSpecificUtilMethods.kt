package org.jetbrains.kastle.utils.intellij

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
object IntellijPlatformSpecificUtilMethods {

    private val methods = mapOf<String, (List<Any?>) -> Any?>(
        "uuid" to { _ -> Uuid.random().toString() },
        "sanitizePackageName" to { args ->
            val invalidPackageNameSymbolPattern = Regex("^\\d|[^a-zA-Z\\d_.]")
            val arg = args.singleOrNull()
                ?: throw IllegalArgumentException("sanitizePackageName requires one argument")
            // keep in sync with org.jetbrains.idea.devkit.module.webstarter.IdePluginModuleWebBasedBuilder#sanitizeThemeFilename
            arg.toString().replace("-", "")
                .replace(invalidPackageNameSymbolPattern, "_")
                .replace(Regex("\\s"), "")
        }
    )

    fun supportsStaticMethod(methodName: String): Boolean {
        return methodName in methods
    }

    fun evaluateStaticMethod(methodName: String, args: List<Any?>): Any? {
        return methods[methodName]?.invoke(args)
    }

}
