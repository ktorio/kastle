package kastle.chatApp.ui.utils

import com.intellij.icons.AllIcons
import javax.swing.Icon

/**
 * Centralized icons used by the Chat sample UI.
 * Grouped by feature area to keep call-sites tidy and consistent.
 */
object ChatAppIcons {
    object Header {
        val search: Icon = AllIcons.Actions.Find
        val close: Icon = AllIcons.Actions.Cancel
    }

    object Search {
        val previous: Icon = AllIcons.Actions.PreviousOccurence
        val next: Icon = AllIcons.Actions.NextOccurence
    }

    object Prompt {
        val send: Icon = AllIcons.RunConfigurations.TestState.Run
        val stop: Icon = AllIcons.Run.Stop
    }
}
