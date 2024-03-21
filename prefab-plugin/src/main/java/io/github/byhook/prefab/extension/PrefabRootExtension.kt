package io.github.byhook.prefab.extension

import io.github.byhook.prefab.utils.PrefabUtils
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

    val dependsOnTask: LinkedHashSet<String> by lazy {
        LinkedHashSet()
    }

    open val prefabModulesMap by lazy {
        mutableMapOf<String, PrefabModulesExtension>()
    }

    fun dependsOn(dependTaskName: String) {
        dependsOnTask.add(dependTaskName)
    }

    fun module(moduleLibName: String,
        @PrefabLibraryType libMode: Int,
        block: (PrefabModulesExtension.() -> Unit)? = null) {
        module(moduleLibName, moduleLibName, libMode, block)
    }

    /**
     * 默认libName和libFileName是一致的
     * 即：
     * 库名：lame
     * 库文件名：lib${name}
     * libName这个对应的是prefab/modules下的库名
     * libFileName对应的是实际的文件名(不含后缀名)
     */
    fun module(moduleLibName: String,
        libraryName: String = moduleLibName,
        @PrefabLibraryType libMode: Int,
        block: (PrefabModulesExtension.() -> Unit)? = null) {
        bindPrefabModuleExt(moduleLibName, libraryName, libMode, block)
    }

    private fun bindPrefabModuleExt(
        moduleLibName: String,
        libraryName: String,
        @PrefabLibraryType libMode: Int,
        block: (PrefabModulesExtension.() -> Unit)? = null) {
        when (libMode) {
            PrefabLibraryType.ALL -> {
                //生成静态库配置
                bindPrefabModuleExt(moduleLibName, libraryName, true, block)
                //生成动态库配置
                bindPrefabModuleExt(moduleLibName, libraryName, false, block)
            }

            else -> {
                val isStatic = libMode == PrefabLibraryType.STATIC
                bindPrefabModuleExt(moduleLibName, libraryName, isStatic, block)
            }
        }
    }

    private fun bindPrefabModuleExt(
        moduleLibName: String,
        libraryName: String,
        isStatic: Boolean,
        block: (PrefabModulesExtension.() -> Unit)? = null) {
        val extensionName = if (isStatic) ".a" else ".so"
        val resultLibName = "$moduleLibName$extensionName"
        val resultLibraryName = PrefabUtils.getLibraryName(libraryName)
        println("module moduleLibName:$resultLibName libraryName:$resultLibraryName")
        val staticModuleConfig = PrefabModulesExtension(isStatic, resultLibraryName)
        block?.invoke(staticModuleConfig)
        //静态库缓存起来
        prefabModulesMap[resultLibName] = staticModuleConfig
    }

    fun modules(libNameMap: Map<String, String>,
        @PrefabLibraryType libMode: Int,
        block: (PrefabModulesExtension.() -> Unit)? = null) {
        libNameMap.forEach {
            val moduleLibName = it.key
            val libraryName = it.value
            bindPrefabModuleExt(moduleLibName, libraryName, libMode, block)
        }
    }

    fun modules(libNameList: List<String>,
        @PrefabLibraryType libMode: Int,
        block: (PrefabModulesExtension.() -> Unit)? = null) {
        val transformNameMap = libNameList.associateWith { it }
        modules(transformNameMap, libMode, block)
    }

}
