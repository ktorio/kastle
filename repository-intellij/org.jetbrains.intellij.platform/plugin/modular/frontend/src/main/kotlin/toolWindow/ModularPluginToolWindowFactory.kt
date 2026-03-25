package org.jetbrains.plugins.template.toolWindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import org.jetbrains.jewel.bridge.addComposeTab
import org.jetbrains.plugins.template.CoroutineScopeHolder
import org.jetbrains.plugins.template.chatApp.ChatAppSample
import org.jetbrains.plugins.template.chatApp.viewmodel.ChatViewModel
import org.jetbrains.plugins.template.chatApp.viewmodel.FrontendChatRepositoryModel

class ModularPluginToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun shouldBeAvailable(project: Project) = true

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        chatApp(project, toolWindow)
    }

    private fun chatApp(project: Project, toolWindow: ToolWindow) {
        val viewModel = ChatViewModel(
            CoroutineScopeHolder.getInstance(project).createScope(ChatViewModel::class.java.simpleName),
            FrontendChatRepositoryModel.getInstance(project)
        )
        Disposer.register(toolWindow.disposable, viewModel)

        toolWindow.addComposeTab("Chat App") {
            ChatAppSample(viewModel)
        }
    }
}
