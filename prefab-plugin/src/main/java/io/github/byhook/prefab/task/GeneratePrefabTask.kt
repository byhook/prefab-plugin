package io.github.byhook.prefab.task

import io.github.byhook.prefab.PrefabRootConfig
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

open class GeneratePrefabTask() : DefaultTask() {

    private lateinit var prefabRootConfig: PrefabRootConfig

    @Inject
    constructor (prefabRootConfig: PrefabRootConfig) : this() {
        this.prefabRootConfig = prefabRootConfig
    }

    @TaskAction
    fun generatePrefab() {
        println("generatePrefab ${prefabRootConfig.targetPrefabDir}")
    }

}
