package org.jetbrains.kastle.server.ui

import kotlinx.html.FlowContent
import kotlinx.html.LABEL
import kotlinx.html.label
import kotlinx.html.onClick
import kotlinx.html.tabIndex

private val navGroups: MutableSet<String> = mutableSetOf()

fun FlowContent.verticalNavLabel(
    inputId: String,
    navGroup: String,
    block: LABEL.() -> Unit
) = label {
    htmlFor = inputId
    tabIndex = if (navGroups.add(navGroup)) "0" else "-1"

    attributes["data-vertical-arrow-group"] = navGroup

    block()
}

fun FlowContent.horizontalNavLabel(
    inputId: String,
    navGroup: String,
    block: LABEL.() -> Unit
) = label {
    htmlFor = inputId
    tabIndex = if (navGroups.add(navGroup)) "0" else "-1"
    attributes["data-horizontal-arrow-group"] = navGroup

    block()
}