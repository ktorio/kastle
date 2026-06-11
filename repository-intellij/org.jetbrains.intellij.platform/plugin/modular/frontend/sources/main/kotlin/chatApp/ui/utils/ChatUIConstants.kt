package kastle.chatApp.ui.utils

import java.awt.Dimension

object ChatUIConstants {

    object Spacing {
        const val TINY = 2
        const val SMALL = 4
        const val MEDIUM = 6
        const val NORMAL = 8
        const val LARGE = 12
        const val XLARGE = 16
    }

    object MessageBubble {
        const val MIN_WIDTH = 120
        const val MAX_WIDTH = 420
        const val CORNER_RADIUS = 16
        const val VERTICAL_MARGIN = 6
        const val HORIZONTAL_MARGIN = 12
        const val INNER_PADDING = 16

        const val CONTENT_WRAP_WIDTH = MAX_WIDTH - 2 * HORIZONTAL_MARGIN - 2 * INNER_PADDING
    }

    object ThinkingIndicator {
        const val PADDING = 8
        const val DOT_COUNT = 3
        const val DOT_DIAMETER = 6
        const val DOT_SPACING = 4
        const val CYCLE_MS = 900
        const val STAGGER_MS = 200
        const val ANIMATION_PERIOD_MS = 16
        const val MIN_ALPHA = 0.25
        const val MAX_ALPHA = 1.0
    }

    object Button {
        val ACTION_BUTTON_SIZE = Dimension(24, 24)
        val LARGE_ACTION_BUTTON_SIZE = Dimension(28, 28)
        val SEND_BUTTON_SIZE = Dimension(32, 32)
    }

    object Input {
        const val MIN_HEIGHT = 40
        const val MAX_HEIGHT = 100
        const val MIN_WIDTH = 100
        const val TEXT_AREA_PAD_VERTICAL = 4
        const val TEXT_AREA_PAD_HORIZONTAL = 8
        const val BORDER_THICKNESS = 1
    }

    object SearchBar {
        const val FIELD_MAX_WIDTH = 400
        const val FIELD_COLUMNS = 32
    }
}
