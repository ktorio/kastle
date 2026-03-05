package org.jetbrains.kastle.server.ui.wizard

import io.ktor.htmx.html.*
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.html.*
import org.jetbrains.kastle.Group
import org.jetbrains.kastle.PackDescriptor
import org.jetbrains.kastle.PackId

/**
 * Renders the packs panel with search and card grid.
 */
@OptIn(ExperimentalKtorApi::class)
fun FlowContent.wizardPacksPanel(
    basePath: String,
    packs: List<PackDescriptor>,
    selectedPacks: Set<PackId>
) {
    div("wizard-packs-panel") {
        id = "wizard-packs-panel"

        div("wizard-packs-header") {
            h2("wizard-packs-title") {
                +"Features"
            }
            input(type = InputType.text, classes = "wizard-packs-search") {
                id = "wizard-packs-search"
                name = "search"
                placeholder = "Search..."
                attributes.hx {
                    get = "$basePath/packs"
                    trigger = "keyup changed delay:300ms"
                    target = "#wizard-packs-grid"
                    include = "#wizard-plugin-type"
                }
            }
        }

        div("wizard-packs-grid") {
            id = "wizard-packs-grid"
            wizardPacksGrid(basePath, packs, selectedPacks)
        }
    }
}

/**
 * Renders the grid of pack cards, grouped by their groups.
 */
@OptIn(ExperimentalKtorApi::class)
fun FlowContent.wizardPacksGrid(
    basePath: String,
    packs: List<PackDescriptor>,
    selectedPacks: Set<PackId>
) {
    // Group packs by their group
    val groupedPacks = packs.groupBy { it.group }

    // Sort groups: null (ungrouped) first, then by group name
    val sortedGroups = groupedPacks.entries.sortedWith(
        compareBy<Map.Entry<Group?, List<PackDescriptor>>> { it.key == null }
            .thenBy { it.key?.name ?: it.key?.id ?: "" }
    )

    for ((group, groupPacks) in sortedGroups) {
        // Render group header if group exists
        if (group != null) {
            div("wizard-pack-group-header") {
                h3("wizard-pack-group-title") {
                    // TODO group names are not parsed, see https://github.com/ktorio/kastle/issues/73
                    +when (group.id) {
                        "org.jetbrains.intellij.platform.dependencies" -> "Platform dependencies"
                        "org.jetbrains.intellij.platform.plugins" -> "Plugin dependencies"
                        else -> group.name ?: group.id
                    }
                }
            }
        }

        // Render pack cards in this group
        for (pack in groupPacks) {
            wizardPackCard(basePath, pack, pack.id in selectedPacks)
        }
    }
}

/**
 * Renders a single pack card.
 */
@OptIn(ExperimentalKtorApi::class)
fun FlowContent.wizardPackCard(
    basePath: String,
    pack: PackDescriptor,
    selected: Boolean
) {
    val cardId = "wizard-pack-${pack.id.group}-${pack.id.id}"
    val inputId = "wizard-pack-toggle-${pack.id.group}-${pack.id.id}"

    div("wizard-pack-card${if (selected) " selected" else ""}") {
        id = cardId
        attributes["data-pack-id"] = pack.id.toString()

        // Hidden checkbox for selection state
        input(type = InputType.checkBox, classes = "wizard-pack-checkbox") {
            this.id = inputId
            name = "wizard-pack"
            value = pack.id.toString()
            checked = selected
            attributes["data-pack-id"] = pack.id.toString()
            onChange = "wizardTogglePack(this)"
        }

        label("wizard-pack-label") {
            htmlFor = inputId

            div("wizard-pack-card-header") {
                div("wizard-pack-icon") {
                    img {
                        src = packIconSrc(pack)
                        alt = pack.name
                        style = "filter: grayscale(100%);"
                    }
                }
                h3("wizard-pack-name") {
                    +pack.name
                }
            }

            pack.description?.let { description ->
                p("wizard-pack-description") {
                    +description
                }
            }
        }

        // Info button to show modal
        button(classes = "wizard-pack-info-btn") {
            type = ButtonType.button
            title = "More info"
            onClick = "wizardShowPackModal('${pack.id}')"
            span {
                unsafe { +"&#8505;" }
            }
        }
    }
}

const val DEFAULT_ICON_PATH = "https://intellij-icons.jetbrains.design/icons/AllIcons/expui/general/externalTools.svg"

private fun packIconSrc(pack: PackDescriptor): String {
    // TODO: it turned out that some icons don't have ExpUI icons; think about a better solution for this
    return when (pack.icon) {
        "AllIcons.Language.GO" -> "https://intellij-icons.jetbrains.design/icons/AllIcons/language/go.svg"
        "AllIcons.Language.Kotlin" -> "https://intellij-icons.jetbrains.design/icons/KotlinBaseResourcesIcons/org/jetbrains/kotlin/idea/icons/expui/kotlin.svg"
        "AllIcons.Language.Python" -> "https://intellij-icons.jetbrains.design/icons/AllIcons/language/python.svg"
        "AllIcons.Language.Ruby" -> "https://intellij-icons.jetbrains.design/icons/AllIcons/language/ruby.svg"
        "AllIcons.Language.Rust" -> "https://intellij-icons.jetbrains.design/icons/AllIcons/language/rust.svg"
        else -> {
            val (containerClass, path) = pack.icon?.split(".", limit = 2) ?: return DEFAULT_ICON_PATH
            val pathSegments = path.split(".")
            val iconRelativePath = pathSegments.joinToString("/") { it.first().lowercase() + it.substring(1) }
            "https://intellij-icons.jetbrains.design/icons/$containerClass/expui/$iconRelativePath.svg"
        }
    }
}

/**
 * Renders the packs grid for HTMX partial update.
 */
fun HTML.wizardPacksGridHtml(
    basePath: String,
    packs: List<PackDescriptor>,
    selectedPacks: Set<PackId>
) {
    body {
        wizardPacksGrid(basePath, packs, selectedPacks)
    }
}
