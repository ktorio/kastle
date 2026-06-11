package kastle.chatApp.ui.utils

import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JButton

object ButtonUtils {

    fun applyHoverEffect(button: JButton) {
        button.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                if (button.isEnabled) {
                    button.isContentAreaFilled = true
                    button.background = JBUI.CurrentTheme.ActionButton.hoverBackground()
                }
            }

            override fun mouseExited(e: MouseEvent) {
                button.isContentAreaFilled = false
            }
        })
    }

    fun createActionButton(
        icon: Icon,
        tooltip: String,
        size: Dimension = Dimension(24, 24),
        action: () -> Unit
    ): JButton {
        return JButton(icon).apply {
            toolTipText = tooltip
            isBorderPainted = false
            isContentAreaFilled = false
            isFocusable = false
            preferredSize = size
            addActionListener { action() }
            applyHoverEffect(this)
        }
    }
}
