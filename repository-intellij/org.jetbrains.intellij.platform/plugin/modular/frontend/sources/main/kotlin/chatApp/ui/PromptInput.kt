package kastle.chatApp.ui

import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import kastle.ModularPluginFrontendBundle
import kastle.chatApp.ui.utils.ButtonUtils
import kastle.chatApp.ui.utils.ChatAppColors
import kastle.chatApp.ui.utils.ChatAppIcons
import kastle.chatApp.ui.utils.ChatUIConstants
import kastle.chatApp.viewmodel.MessageInputState
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.KeyStroke
import javax.swing.border.CompoundBorder
import javax.swing.border.LineBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class PromptInput(
    private val onInputChanged: (String) -> Unit,
    private val onSend: (String) -> Unit,
    private val onStop: (String) -> Unit
) : JPanel() {

    private val textArea: JBTextArea
    private val scrollPane: JBScrollPane
    private val sendButton: JButton

    private var currentState: MessageInputState = MessageInputState.Enabled("")
    private var skipInputChangeUpdate = false

    init {
        setupAppearance()

        textArea = createTextArea()
        scrollPane = createScrollPane(textArea)
        sendButton = createSendButton()

        add(scrollPane, BorderLayout.CENTER)
        add(sendButton, BorderLayout.EAST)

        setupKeyBindings()
    }

    private fun setupAppearance() {
        layout = BorderLayout(ChatUIConstants.Spacing.MEDIUM, 0)
        border = CompoundBorder(
            LineBorder(ChatAppColors.Prompt.border, ChatUIConstants.Input.BORDER_THICKNESS, true),
            JBUI.Borders.empty(ChatUIConstants.Spacing.MEDIUM, ChatUIConstants.Spacing.NORMAL)
        )
        background = ChatAppColors.Panel.background
    }

    private fun createTextArea() = JBTextArea().apply {
        lineWrap = true
        wrapStyleWord = true
        rows = 1
        border = JBUI.Borders.empty(
            ChatUIConstants.Input.TEXT_AREA_PAD_VERTICAL,
            ChatUIConstants.Input.TEXT_AREA_PAD_HORIZONTAL
        )

        document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = handleTextChange()
            override fun removeUpdate(e: DocumentEvent?) = handleTextChange()
            override fun changedUpdate(e: DocumentEvent?) = handleTextChange()
        })
    }

    private fun createScrollPane(content: JComponent) = object : JBScrollPane(content) {
        override fun getPreferredSize(): Dimension {
            val base = super.getPreferredSize()
            val capped = base.height.coerceIn(
                ChatUIConstants.Input.MIN_HEIGHT,
                ChatUIConstants.Input.MAX_HEIGHT
            )
            return Dimension(base.width, capped)
        }
    }.apply {
        verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        border = LineBorder(
            JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(),
            ChatUIConstants.Input.BORDER_THICKNESS
        )
        minimumSize = Dimension(ChatUIConstants.Input.MIN_WIDTH, ChatUIConstants.Input.MIN_HEIGHT)
    }

    private fun createSendButton() = JButton().apply {
        icon = ChatAppIcons.Prompt.send
        isEnabled = false
        preferredSize = ChatUIConstants.Button.SEND_BUTTON_SIZE
        toolTipText = ModularPluginFrontendBundle.message("chat.prompt.send.tooltip")
        isFocusable = false
        isBorderPainted = false
        isContentAreaFilled = false
        addActionListener { handleButtonClick() }
        ButtonUtils.applyHoverEffect(this)
    }

    private fun handleTextChange() {
        if (skipInputChangeUpdate) {
            skipInputChangeUpdate = false
            return
        }

        val text = textArea.text
        onInputChanged(text)

        sendButton.isEnabled = currentState != MessageInputState.Disabled && text.isNotBlank()
    }

    private fun setupKeyBindings() {
        val inputMap = textArea.getInputMap(JComponent.WHEN_FOCUSED)
        val actionMap = textArea.actionMap

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "send")
        actionMap.put("send", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                val text = textArea.text.trim()
                if (text.isEmpty()) return
                when (currentState) {
                    is MessageInputState.Sending -> onStop(text)
                    else -> handleSend()
                }
            }
        })

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), "newline")
        actionMap.put("newline", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                skipInputChangeUpdate = true
                textArea.insert("\n", textArea.caretPosition)
            }
        })
    }

    fun updateState(state: MessageInputState) {
        currentState = state

        when (state) {
            MessageInputState.Disabled -> applySendButtonStyle(SendButtonStyle.Idle, textAreaEnabled = true, forceEnabled = false)
            is MessageInputState.Enabled,
            is MessageInputState.Sent,
            is MessageInputState.SendFailed -> applySendButtonStyle(SendButtonStyle.Ready, textAreaEnabled = true, forceEnabled = false)
            is MessageInputState.Sending -> applySendButtonStyle(SendButtonStyle.Stop, textAreaEnabled = false, forceEnabled = true)
        }
    }

    private fun applySendButtonStyle(style: SendButtonStyle, textAreaEnabled: Boolean, forceEnabled: Boolean) {
        val hasText = textArea.text.isNotBlank()
        sendButton.icon = style.iconFor(hasText)
        sendButton.toolTipText = ModularPluginFrontendBundle.message(style.tooltipKey)
        sendButton.isEnabled = forceEnabled || (style.allowsSend && hasText)
        textArea.isEnabled = textAreaEnabled
    }

    private fun handleButtonClick() {
        when (currentState) {
            is MessageInputState.Sending -> handleStop()
            else -> handleSend()
        }
    }

    private fun handleSend() {
        val text = textArea.text.trim()
        if (text.isEmpty()) return

        onSend(text)
        skipInputChangeUpdate = true
        textArea.text = ""
    }

    private fun handleStop() {
        onStop(textArea.text.trim())
    }

    private enum class SendButtonStyle(val tooltipKey: String, val allowsSend: Boolean) {
        Idle("chat.prompt.send.tooltip", allowsSend = false),
        Ready("chat.prompt.send.tooltip", allowsSend = true),
        Stop("chat.prompt.stop.tooltip", allowsSend = false);

        fun iconFor(hasText: Boolean): Icon = when (this) {
            Idle -> ChatAppIcons.Prompt.send
            Ready -> if (hasText) AllIcons.Actions.Execute else ChatAppIcons.Prompt.send
            Stop -> ChatAppIcons.Prompt.stop
        }
    }
}
