package io.github.byhook.prefab

import io.github.byhook.prefab.extension.PrefabRootExtension
import io.github.byhook.prefab.task.GeneratePrefabTask
import io.github.byhook.prefab.task.GenerateZipTask
import org.gradle.api.Plugin
import org.gradle.api.Project

class PrefabPackPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        println("apply prefab pack plugin!")
        //创建扩展配置
        val prefabRootConfig = target.extensions.create("generatePrefab", PrefabRootExtension::class.java)
        //配置任务依赖
        val generateZipTask = target.tasks.register("generateZipTask", GenerateZipTask::class.java)
        val generatePrefabTask = target.tasks.register("generatePrefab",
            GeneratePrefabTask::class.java,
            prefabRootConfig
        )
        generateZipTask.configure {
            it.dependsOn(generatePrefabTask)
        }
        target.afterEvaluate {
            println("apply afterEvaluate")
        }
    }

}