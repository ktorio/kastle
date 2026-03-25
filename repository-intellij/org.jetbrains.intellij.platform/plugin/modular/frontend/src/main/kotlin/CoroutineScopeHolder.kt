package org.jetbrains.plugins.template

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/**
 * A service-level class that provides and manages coroutine scopes for a given project.
 *
 * @constructor Initializes the [CoroutineScopeHolder] with a project-wide coroutine scope.
 * @param projectWideCoroutineScope A [CoroutineScope] defining the lifecycle of project-wide coroutines.
 */
@Service(Level.PROJECT)
internal class CoroutineScopeHolder(private val projectWideCoroutineScope: CoroutineScope) {
    companion object {
        fun getInstance(project: Project): CoroutineScopeHolder {
            return project.getService(CoroutineScopeHolder::class.java)
        }
    }

    /**
     * Creates a new coroutine scope as a child of the project-wide coroutine scope with the specified name.
     *
     * @param name The name for the newly created coroutine scope.
     * @return a scope with a [Job] which parent is the [Job] of [projectWideCoroutineScope] scope.
     */
    @Suppress("UnstableApiUsage")
    fun createScope(name: String): CoroutineScope = projectWideCoroutineScope.childScope(name)

    fun getPluginScope(): CoroutineScope = projectWideCoroutineScope
}
