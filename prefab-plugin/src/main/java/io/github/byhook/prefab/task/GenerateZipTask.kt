package io.github.byhook.prefab.task

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

open class GenerateZipTask : DefaultTask() {

    @TaskAction
    fun generateDirectory() {
        println("generateDirectory")
        println("generateDirectory end")
    }

}
