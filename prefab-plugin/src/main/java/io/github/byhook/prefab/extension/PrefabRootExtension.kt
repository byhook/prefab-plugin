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
        static: Boolean,
        block: (PrefabModulesExtension.() -> Unit)? = null) {
        val extensionName = if (static) ".a" else ".so"
        val resultLibName = "$moduleLibName$extensionName"
        val resultLibraryName = PrefabUtils.getLibraryName(libraryName)
        println("module moduleLibName:$resultLibName libraryName:$resultLibraryName")
        val targetModuleConfig = PrefabModulesExtension(static, resultLibraryName)
        block?.invoke(targetModuleConfig)
        prefabModulesMap[resultLibName] = targetModuleConfig
    }

    fun modules(libNameMap: Map<String, String>,
        @PrefabLibMode libMode: Int,
        block: (PrefabModulesExtension.() -> Unit)? = null) {
        libNameMap.forEach {
            val moduleLibName = it.key
            val libraryName = it.value
            val resultLibraryName = PrefabUtils.getLibraryName(libraryName)
            when (libMode) {
                PrefabLibMode.LIB_MODE_ALL -> {
                    val staticModuleConfig = PrefabModulesExtension(true, resultLibraryName)
                    block?.invoke(staticModuleConfig)
                    //静态库缓存起来
                    prefabModulesMap["${moduleLibName}.a"] = staticModuleConfig
                    val dynamicModuleConfig = PrefabModulesExtension(false, resultLibraryName)
                    block?.invoke(dynamicModuleConfig)
                    //动态库缓存起来
                    prefabModulesMap["${moduleLibName}.so"] = dynamicModuleConfig
                }

                else -> {
                    val isStatic = libMode == PrefabLibMode.LIB_MODE_STATIC
                    val extensionName = if (isStatic) ".a" else ".so"
                    val retLibName = "$moduleLibName$extensionName"
                    //动态库或者静态库
                    val targetModuleConfig = PrefabModulesExtension(isStatic, resultLibraryName)
                    block?.invoke(targetModuleConfig)
                    //缓存起来
                    prefabModulesMap[retLibName] = targetModuleConfig
                }
            }
        }
    }

    fun modules(libNameList: List<String>,
        @PrefabLibMode libMode: Int,
        block: (PrefabModulesExtension.() -> Unit)? = null) {
        val transformNameMap = libNameList.associateWith { it }
        modules(transformNameMap, libMode, block)
    }

}
