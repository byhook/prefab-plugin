package io.github.byhook.prefab.extension

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import java.io.File

open class PrefabRootExtension {

    /**
     * 生成目标prefab库的路径
     */
    lateinit var prefabDir: Provider<Directory>

    /**
     * 生成目标prefab库产物的路径
     */
    lateinit var prefabArtifactDir: Provider<Directory>

    lateinit var abiList: List<String>

    lateinit var manifestFile: File

    lateinit var prefabName: String

    lateinit var prefabVersion: String

    open val prefabModulesMap by lazy {
        mutableMapOf<String, PrefabModulesExtension>()
    }

    fun module(name: String, static: Boolean, block: PrefabModulesExtension.() -> Unit) {
        println("module name:$name")
        val targetModuleConfig = PrefabModulesExtension(static)
        block.invoke(targetModuleConfig)
        prefabModulesMap[name] = targetModuleConfig
    }

}
