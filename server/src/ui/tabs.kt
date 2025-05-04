package org.jetbrains.kastle.server.ui

import kotlinx.html.*

fun FlowContent.tabList(
    name: String,
    tabs: TabsContent.() -> Unit
) {
    div("tabs") {
        val tabsContent = TabsContent(this).also(tabs)

        for (tab in tabsContent) {
            input(type = InputType.radio, name = name) {
                this.id = "${tab.id}-tab"
                this.checked = tab.checked
            }
        }

        div("tabs-header") {
            for (tab in tabsContent) {
                horizontalNavLabel("${tab.id}-tab", "main-tabs") {
                    div { +tab.title }
                    // TODO div("icon") {}
                }
            }
        }

        div("tabs-content") {
            for (tab in tabsContent) {
                div("tab-content") {
                    id = tab.id
                    tab.contents(this)
                }
            }
        }
    }
}

class TabsContent(private val flowContent: FlowContent): FlowContent by flowContent, Iterable<TabsContent.Tab> {
    private val tabs = mutableListOf<Tab>()

    fun tab(id: String, title: String, checked: Boolean = false, contents: DIV.() -> Unit) {
        tabs += Tab(id, title, checked, contents)
    }

    override fun iterator(): Iterator<Tab> =
        tabs.iterator()

    class Tab(
        val id: String,
        val title: String,
        val checked: Boolean = false,
        val contents: DIV.() -> Unit
    )
}