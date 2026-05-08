package org.jetbrains.kastle.kotlin

import org.jetbrains.kastle.SourceImport
import org.jetbrains.kastle.SourceTemplate
import org.jetbrains.kastle.Url
import org.jetbrains.kastle.toString
import org.jetbrains.kastle.utils.parentPath

const val KT_EXTENSION = "kt"
const val KT_SCRIPT_EXTENSION = "kts"

/**
 * Takes extra imports from external sources (slots) and merges them into the given source template's preamble.
 *
 * Also includes the package name, based on the group and source folder.
 */
fun Appendable.writeKotlinSourcePreamble(
    groupId: String,
    target: Url,
    source: SourceTemplate,
    extraImports: List<SourceImport>,
    skipPackage: Boolean,
): Int {
    val dir = target.parentPath
        .replace(Regex("^/?/?(?:(?:src|test)(?:@\\w+)?)?(?:/\\w*(?:main|test)/\\w+)?/?", RegexOption.IGNORE_CASE), "")
        .replace('/', '.')
        .removePrefix(groupId) // when using nested structure

    val pkg = if (dir.isEmpty()) groupId else "${groupId}.$dir"

    if (!skipPackage)
        append("package $pkg")

    val importsDeclaration = source.imports ?: return 0
    val sourceImports = importsDeclaration.imports
    val imports: List<String> = (sourceImports + extraImports).map {
        it.toString(groupId)
    }.distinct()

    if (imports.isNotEmpty()) {
        if (!skipPackage)
            append("\n\n")
        append(imports.joinToString("\n"))
    }

    return importsDeclaration.position.range.last
}
