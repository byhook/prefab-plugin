package io.github.byhook.prefab

import io.github.byhook.prefab.task.GenerateModulesTask
import io.github.byhook.prefab.task.GeneratePrefabTask
import io.github.byhook.prefab.task.GenerateZipTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task

class PrefabPackPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        println("apply prefab pack plugin!")

        val prefabRootConfig = target.extensions.create("packPrefab", PrefabRootConfig::class.java)

        val generateModulesTask = target.tasks.register("generateModules", GenerateModulesTask::class.java)
        val generateZipTask = target.tasks.register("generateZipTask", GenerateZipTask::class.java)
        val generatePrefabTask = target.tasks.register("generatePrefab",
            GeneratePrefabTask::class.java,
            prefabRootConfig
        )
        generatePrefabTask.configure {
            it.dependsOn(generateZipTask)
            it.dependsOn(generateModulesTask)
        }
        target.afterEvaluate {
            println("apply afterEvaluate")
        }
    }

}