package io.github.byhook.prefab.task

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

open class GenerateModulesTask : DefaultTask() {

    @TaskAction
    fun generateModules() {
        println("generateModules")
    }

}
