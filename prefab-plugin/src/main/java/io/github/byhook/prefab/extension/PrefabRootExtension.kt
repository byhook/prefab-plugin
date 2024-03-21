package io.github.byhook.prefab.extension

import org.gradle.api.file.Directory
import java.io.File

open class PrefabRootExtension {

    /**
     * 源库目录
     */
    lateinit var sourceLibsDir: Directory

    /**
     * 源头文件目录
     */
    lateinit var sourceIncsDir: Directory

    /**
     * 生成目标prefab库的路径
     */
    lateinit var prefabBuildDir: Directory

    /**
     * 生成目标prefab库产物的路径
     */
    lateinit var prefabArtifactDir: Directory

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
