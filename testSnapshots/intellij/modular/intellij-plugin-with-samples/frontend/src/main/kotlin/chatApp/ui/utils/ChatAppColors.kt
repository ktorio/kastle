package com.acme.chatApp.ui.utils

import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import java.awt.Color
import javax.swing.UIManager

object ChatAppColors {
    object Panel {
        val background: Color
            get() = UIManager.getColor("Panel.background") ?: JBColor.PanelBackground
    }

    object Text {
        val disabled: Color
            get() = JBColor.GRAY

        val normal: Color
            get() = UIManager.getColor("Label.foreground") ?: JBColor.foreground()

        val timestamp: Color = JBColor(Gray._192, Gray._160)

        val authorName: Color = JBColor(Color(219, 224, 235), Color(180, 200, 220))
    }

    object MessageBubble {
        val myBackground: Color
            get() = JBColor(
                Color(227, 242, 253),
                Color(37, 55, 70)
            )

        val othersBackground: Color
            get() = JBColor(
                Gray._245,
                Color(50, 50, 52)
            )

        val myBackgroundBorder: Color
            get() = JBColor(
                Color(144, 202, 249),
                Color(66, 165, 245)
            )

        val othersBackgroundBorder: Color
            get() = JBColor(
                Gray._189,
                Gray._97
            )

        val mySearchHighlightedBackground: Color
            get() = JBColor(
                Color(179, 229, 252),
                Color(58, 96, 115)
            )

        val othersSearchHighlightedBackground: Color
            get() = JBColor(
                Gray._224,
                Color(70, 73, 75)
            )

        val searchHighlightedBackgroundBorder: Color = JBColor(
            Color(66, 165, 245),
            Color(100, 181, 246)
        )

        val matchingMyBorder: Color
            get() = JBColor(
                Color(144, 202, 249),
                Color(79, 195, 247)
            )

        val matchingOthersBorder: Color
            get() = JBColor(
                Gray._158,
                Gray._117
            )
    }

    object Prompt {
        val border: Color = JBColor.border()
    }
}
