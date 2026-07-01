package kastle.chatApp.ui

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import kastle.ChatMessage
import kastle.ModularPluginFrontendBundle
import kastle.chatApp.ui.utils.ChatAppColors
import kastle.chatApp.ui.utils.ChatUIConstants
import java.awt.*
import java.awt.geom.RoundRectangle2D
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JPanel

class MessageBubble(
    private val message: ChatMessage,
    private var isMatchingSearch: Boolean = false,
    private var isHighlightedInSearch: Boolean = false
) : JPanel() {

    private val isMyMessage = message.isMyMessage

    init {
        setupAppearance()

        add(AuthorName(message))
        add(Box.createVerticalStrut(JBUI.scale(ChatUIConstants.Spacing.MEDIUM)))

        when {
            message.isTextMessage() -> {
                add(MessageContent(message))
                add(Box.createVerticalStrut(JBUI.scale(ChatUIConstants.Spacing.NORMAL)))
                add(TimeStampLabel(message))
            }
            message.isAIThinkingMessage() -> add(ThinkingIndicator())
        }
    }

    private fun setupAppearance() {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false

        border = JBUI.Borders.compound(
            JBUI.Borders.empty(
                ChatUIConstants.MessageBubble.VERTICAL_MARGIN,
                ChatUIConstants.MessageBubble.HORIZONTAL_MARGIN
            ),
            JBUI.Borders.empty(ChatUIConstants.MessageBubble.INNER_PADDING)
        )

        minimumSize = Dimension(JBUI.scale(ChatUIConstants.MessageBubble.MIN_WIDTH), 0)
        maximumSize = Dimension(JBUI.scale(ChatUIConstants.MessageBubble.MAX_WIDTH), Int.MAX_VALUE)
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)

        val g2d = g.create() as Graphics2D
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val margin = JBUI.scale(ChatUIConstants.MessageBubble.VERTICAL_MARGIN)
        val marginH = JBUI.scale(ChatUIConstants.MessageBubble.HORIZONTAL_MARGIN)
        val cornerRadius = JBUI.scale(ChatUIConstants.MessageBubble.CORNER_RADIUS)

        val shape = RoundRectangle2D.Float(
            marginH.toFloat(),
            margin.toFloat(),
            (width - 2 * marginH).toFloat(),
            (height - 2 * margin).toFloat(),
            cornerRadius.toFloat(),
            cornerRadius.toFloat()
        )

        g2d.color = getMessageBackground()
        g2d.fill(shape)

        g2d.color = getBorderColor()
        g2d.stroke = BasicStroke(JBUI.scale(1).toFloat())
        g2d.draw(shape)

        g2d.dispose()
    }

    private fun getMessageBackground(): Color {
        return when {
            isHighlightedInSearch && isMyMessage -> ChatAppColors.MessageBubble.mySearchHighlightedBackground
            isHighlightedInSearch && !isMyMessage -> ChatAppColors.MessageBubble.othersSearchHighlightedBackground
            isMyMessage -> ChatAppColors.MessageBubble.myBackground
            else -> ChatAppColors.MessageBubble.othersBackground
        }
    }

    private fun getBorderColor(): Color {
        return when {
            isHighlightedInSearch -> ChatAppColors.MessageBubble.searchHighlightedBackgroundBorder
            isMatchingSearch && isMyMessage -> ChatAppColors.MessageBubble.matchingMyBorder
            isMatchingSearch && !isMyMessage -> ChatAppColors.MessageBubble.matchingOthersBorder
            isMyMessage -> ChatAppColors.MessageBubble.myBackgroundBorder
            else -> ChatAppColors.MessageBubble.othersBackgroundBorder
        }
    }

    fun updateSearchState(matching: Boolean, highlighted: Boolean) {
        isMatchingSearch = matching
        isHighlightedInSearch = highlighted
        repaint()
    }
}

private class AuthorName(message: ChatMessage) : JBLabel() {
    init {
        text = if (message.isMyMessage) {
            ModularPluginFrontendBundle.message("chat.message.author.me")
        } else {
            message.author
        }

        font = JBFont.small().asBold()
        foreground = ChatAppColors.Text.authorName
        alignmentX = LEFT_ALIGNMENT
    }
}

private class MessageContent(message: ChatMessage) : JBTextArea() {
    init {
        text = message.content
        font = JBFont.regular()
        alignmentX = LEFT_ALIGNMENT

        isEditable = false
        isFocusable = false
        isOpaque = false
        lineWrap = true
        wrapStyleWord = true
        border = null

        size = Dimension(JBUI.scale(ChatUIConstants.MessageBubble.CONTENT_WRAP_WIDTH), Short.MAX_VALUE.toInt())
    }

    override fun getPreferredSize(): Dimension {
        val width = JBUI.scale(ChatUIConstants.MessageBubble.CONTENT_WRAP_WIDTH)
        size = Dimension(width, Short.MAX_VALUE.toInt())
        return Dimension(width, super.getPreferredSize().height)
    }

    override fun getMaximumSize(): Dimension = preferredSize
}

private class TimeStampLabel(message: ChatMessage) : JPanel() {
    init {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        isOpaque = false
        alignmentX = LEFT_ALIGNMENT

        val label = JBLabel(message.formattedTime()).apply {
            font = JBFont.small()
            foreground = ChatAppColors.Text.timestamp
        }
        add(Box.createHorizontalGlue())
        add(label)
    }
}
