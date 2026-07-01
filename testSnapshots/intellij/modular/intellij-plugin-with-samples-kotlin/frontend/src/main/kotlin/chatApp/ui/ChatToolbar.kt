package com.acme.chatApp.ui

import com.intellij.util.ui.JBUI
import com.acme.ModularPluginFrontendBundle
import com.acme.chatApp.ui.utils.ButtonUtils
import com.acme.chatApp.ui.utils.ChatAppColors
import com.acme.chatApp.ui.utils.ChatAppIcons
import com.acme.chatApp.ui.utils.ChatUIConstants
import com.acme.chatApp.viewmodel.ChatViewModel
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.border.CompoundBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class ChatToolbar(private val viewModel: ChatViewModel) : JPanel() {
    private val searchBar: ChatSearchBar

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)

        val headerPanel = ChatHeader(onToggleSearch = { visible -> toggleSearch(visible) })

        searchBar = ChatSearchBar(
            onSearchQueryChange = { query -> viewModel.searchChatMessagesHandler().onSearchQuery(query) },
            onNextResult = { viewModel.searchChatMessagesHandler().onNavigateToNextSearchResult() },
            onPreviousResult = { viewModel.searchChatMessagesHandler().onNavigateToPreviousSearchResult() },
            onCloseSearch = {
                viewModel.searchChatMessagesHandler().onStopSearch()
                toggleSearch(false)
            }
        )

        add(headerPanel)
        add(searchBar)
    }

    private fun toggleSearch(visible: Boolean) {
        if (visible) {
            viewModel.searchChatMessagesHandler().onStartSearch()
        } else {
            viewModel.searchChatMessagesHandler().onStopSearch()
        }

        searchBar.isVisible = visible
        revalidate()
        repaint()
    }

    fun updateSearchState(searchState: SearchState) {
        searchBar.updateSearchState(searchState)
    }
}

private class ChatSearchBar(
    private val onSearchQueryChange: (String) -> Unit,
    private val onNextResult: () -> Unit,
    private val onPreviousResult: () -> Unit,
    private val onCloseSearch: () -> Unit
) : JPanel() {

    private val searchField: SearchTextField
    private val resultLabel: JLabel
    private val prevButton: JButton
    private val nextButton: JButton

    init {
        setupAppearance()

        searchField = SearchTextField(onSearchQueryChange)

        resultLabel = JLabel("").apply {
            foreground = ChatAppColors.Text.disabled
            font = font.deriveFont(12f)
            border = JBUI.Borders.emptyLeft(ChatUIConstants.Spacing.NORMAL)
        }

        prevButton = ButtonUtils.createActionButton(
            icon = ChatAppIcons.Search.previous,
            tooltip = "Previous (Shift+F3)",
            size = ChatUIConstants.Button.ACTION_BUTTON_SIZE,
            action = onPreviousResult
        )

        nextButton = ButtonUtils.createActionButton(
            icon = ChatAppIcons.Search.next,
            tooltip = "Next (F3, Enter)",
            size = ChatUIConstants.Button.ACTION_BUTTON_SIZE,
            action = onNextResult
        )

        val closeButton = ButtonUtils.createActionButton(
            icon = ChatAppIcons.Header.close,
            tooltip = ModularPluginFrontendBundle.message("chat.close.search.button"),
            size = ChatUIConstants.Button.ACTION_BUTTON_SIZE,
            action = onCloseSearch
        )

        add(searchField)
        add(resultLabel)
        add(Box.createHorizontalStrut(ChatUIConstants.Spacing.SMALL))
        add(prevButton)
        add(nextButton)
        add(Box.createHorizontalStrut(ChatUIConstants.Spacing.SMALL))
        add(closeButton)
        add(Box.createHorizontalGlue())

        setupKeyBindings()
    }

    private fun setupAppearance() {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        border = CompoundBorder(
            JBUI.Borders.customLine(
                JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(),
                0, 0, 1, 0
            ),
            JBUI.Borders.empty(
                ChatUIConstants.Spacing.NORMAL,
                ChatUIConstants.Spacing.XLARGE,
                ChatUIConstants.Spacing.LARGE,
                ChatUIConstants.Spacing.XLARGE
            )
        )
        background = ChatAppColors.Panel.background
        isVisible = false
    }

    private fun setupKeyBindings() {
        val inputMap = searchField.getInputMap(JComponent.WHEN_FOCUSED)
        val actionMap = searchField.actionMap

        // Enter, F3 for next
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "next")
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0), "next")
        actionMap.put("next", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) { onNextResult() }
        })

        // Shift+F3 for previous
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F3, InputEvent.SHIFT_DOWN_MASK), "previous")
        actionMap.put("previous", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) { onPreviousResult() }
        })

        // Escape to close
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close")
        actionMap.put("close", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) { onCloseSearch() }
        })
    }

    fun updateSearchState(searchState: SearchState) {
        val query = searchState.searchQuery
        val hasResults = searchState.hasResults
        val total = searchState.totalResults
        val currentIndex = searchState.currentSearchResultIndex

        when {
            hasResults -> {
                val current = currentIndex + 1
                resultLabel.text = "$current/$total"
                prevButton.isEnabled = total > 1
                nextButton.isEnabled = total > 1
            }
            query?.isNotBlank() == true -> {
                resultLabel.text = ModularPluginFrontendBundle.message("chat.no.results")
                prevButton.isEnabled = false
                nextButton.isEnabled = false
            }
            else -> {
                resultLabel.text = ""
                prevButton.isEnabled = false
                nextButton.isEnabled = false
            }
        }
    }
}

private class SearchTextField(private val onTextChange: (String) -> Unit) : JTextField() {
    init {
        setupAppearance()

        document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = handleTextChange()
            override fun removeUpdate(e: DocumentEvent?) = handleTextChange()
            override fun changedUpdate(e: DocumentEvent?) = handleTextChange()
        })
    }

    private fun setupAppearance() {
        toolTipText = ModularPluginFrontendBundle.message("chat.search.placeholder")
        maximumSize = Dimension(ChatUIConstants.SearchBar.FIELD_MAX_WIDTH, preferredSize.height)
        columns = ChatUIConstants.SearchBar.FIELD_COLUMNS
    }

    private fun handleTextChange() {
        onTextChange(text)
    }
}
