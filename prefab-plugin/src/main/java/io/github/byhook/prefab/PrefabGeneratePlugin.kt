package io.github.byhook.prefab

import io.github.byhook.prefab.extension.PrefabRootExtension
import io.github.byhook.prefab.task.CmakeBuildTask
import io.github.byhook.prefab.task.GenerateModulesTask
import io.github.byhook.prefab.task.GeneratePrefabTask
import org.gradle.api.Plugin
import org.gradle.api.Project

class PrefabGeneratePlugin : Plugin<Project> {

    override fun apply(target: Project) {
        println("apply prefab generate plugin!")
        val prefabRootConfig = target.extensions.create("generatePrefab", PrefabRootExtension::class.java)

        target.afterEvaluate {
            if (prefabRootConfig.cmakeConfig != null) {
                if (prefabRootConfig.sourceLibsDir != null || prefabRootConfig.sourceIncsDir != null) {
                    println("WARNING: sourceLibsDir/sourceIncsDir are overridden by cmake output")
                }
                val cmakeBuildTask = target.tasks.register("cmakeBuildTask",
                    CmakeBuildTask::class.java,
                    prefabRootConfig
                )
                val generateModulesTask = target.tasks.register("generateModulesTask",
                    GenerateModulesTask::class.java,
                    prefabRootConfig
                )
                val generatePrefabTask = target.tasks.register("generatePrefabTask",
                    GeneratePrefabTask::class.java,
                    prefabRootConfig
                )
                generateModulesTask.configure {
                    it.dependsOn(cmakeBuildTask)
                    prefabRootConfig.dependsOnTask.forEach { dependTask ->
                        cmakeBuildTask.configure { cmakeTask ->
                            cmakeTask.dependsOn(target.tasks.named(dependTask))
                        }
                    }
                }
                generatePrefabTask.configure {
                    it.dependsOn(generateModulesTask)
                }
            } else {
                if (prefabRootConfig.sourceLibsDir == null || prefabRootConfig.sourceIncsDir == null) {
                    throw IllegalStateException(
                        "sourceLibsDir and sourceIncsDir are required when cmake block is not configured"
                    )
                }
                val generateModulesTask = target.tasks.register("generateModulesTask",
                    GenerateModulesTask::class.java,
                    prefabRootConfig
                )
                val generatePrefabTask = target.tasks.register("generatePrefabTask",
                    GeneratePrefabTask::class.java,
                    prefabRootConfig
                )
                generateModulesTask.configure {
                    prefabRootConfig.dependsOnTask.forEach { dependTask ->
                        it.dependsOn(target.tasks.named(dependTask))
                    }
                }
                generatePrefabTask.configure {
                    it.dependsOn(generateModulesTask)
                }
            }
        }
    }

}
