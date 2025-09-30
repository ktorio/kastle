package org.jetbrains.kastle.templates

/**
 * Helper for handling escaped characters in templates.  This should all be replaced with a proper parser implementation
 * instead of using regexes at some point.
 */
data class ParseContext(
    val template: String,
    val deletions: Collection<Int>,
)

context(_: ParseContext)
val MatchResult.rangeAdjusted get() =
    startAdjusted .. endAdjusted

context(_: ParseContext)
val MatchResult.startAdjusted get() =
    range.first.minusDeletions()

context(_: ParseContext)
val MatchResult.endAdjusted get() =
    range.last.minusDeletions() + 1

context(context: ParseContext)
fun Int.minusDeletions(): Int =
    this - context.deletions.count { it < this }