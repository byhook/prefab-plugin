package io.github.byhook.prefab

import java.io.File

open class PrefabRootConfig {

    /**
     * 生成目标prefab库的路径
     */
    var targetPrefabDir: File? = null

    var abiList: List<String>? = null

    var manifestFile: File? = null

    private val prefabModulesConfig by lazy {
        PrefabModulesConfig()
    }

    fun modules(block: PrefabModulesConfig.() -> Unit) {
        block.invoke(prefabModulesConfig)
    }

}
