package io.github.byhook.prefab

import io.github.byhook.prefab.extension.PrefabRootExtension
import io.github.byhook.prefab.task.GenerateModulesTask
import io.github.byhook.prefab.task.GeneratePrefabTask
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Zip

class PrefabPackPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        println("apply prefab pack plugin!")
        //创建扩展配置
        val prefabRootConfig = target.extensions.create("generatePrefab", PrefabRootExtension::class.java)
        //配置任务依赖
        val generateModulesTask = target.tasks.register("generateModulesTask",
            GenerateModulesTask::class.java,
            prefabRootConfig
        )
        val generatePrefabTask = target.tasks.register("generatePrefabTask",
            GeneratePrefabTask::class.java,
            prefabRootConfig
        )
        generatePrefabTask.configure {
            it.dependsOn(generateModulesTask)
        }
        target.afterEvaluate {
            println("apply afterEvaluate")
        }
    }

}