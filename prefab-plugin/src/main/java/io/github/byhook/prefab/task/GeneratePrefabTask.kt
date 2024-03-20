package io.github.byhook.prefab.task

import io.github.byhook.prefab.extension.PrefabRootExtension
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Zip
import javax.inject.Inject

open class GeneratePrefabTask() : Zip() {

    private lateinit var prefabConfigExt: PrefabRootExtension

    @Inject
    constructor (prefabRootConfig: PrefabRootExtension) : this() {
        this.prefabConfigExt = prefabRootConfig
    }

    @TaskAction
    fun generatePrefab() {
        println("generatePrefab")
    }

}
