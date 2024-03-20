package io.github.byhook.prefab.task

import io.github.byhook.prefab.extension.PrefabRootExtension
import io.github.byhook.prefab.json.Prefab
import io.github.byhook.prefab.json.PrefabAbi
import io.github.byhook.prefab.json.PrefabModule
import io.github.byhook.prefab.utils.JsonUtils
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

open class GeneratePrefabTask() : DefaultTask() {

    private lateinit var prefabConfig: PrefabRootExtension

    private lateinit var prefabDir: Directory

    @Inject
    constructor (prefabRootConfig: PrefabRootExtension) : this() {
        this.prefabConfig = prefabRootConfig
        val targetDir = prefabRootConfig.prefabDir.get().asFile
        val deleteResult = targetDir.deleteRecursively()
        println("delete prefab directory $deleteResult")
        val result = targetDir.mkdirs()
        println("mkdir prefab directory $result")
        prefabDir = prefabRootConfig.prefabDir.get().dir("prefab")
        println("generate prefab directory")
    }

    @TaskAction
    fun generatePrefab() {
        val modulesDir = prefabDir.dir("modules")
        modulesDir.asFile.mkdirs()
        //1、生成prefab.json文件
        val prefab = Prefab().apply {
            this.name = prefabConfig.prefabName
            this.version = prefabConfig.prefabVersion
        }
        val result = JsonUtils.jsonFormat(prefab)
        println("generate => prefab.json: $result")
        prefabDir.file("prefab.json").asFile.writeText(result)
        //2、拷贝AndroidManifest.xml清单文件
        prefabConfig.manifestFile.copyTo(prefabConfig.prefabDir.get()
            .file(prefabConfig.manifestFile.name).asFile)
        println("generate => ${prefabConfig.manifestFile.absolutePath}")
        //3、遍历ABI列表
        prefabConfig.abiList.forEach { abiName ->
            prefabConfig.prefabModulesMap.forEach {
                val libName = it.key
                val moduleConfigExt = it.value
                //例如：modules/lame
                val libNameDir = modulesDir.dir(libName)
                libNameDir.asFile.mkdirs()
                println("generate => libNameDir: $libName")
                val libsDir = libNameDir.dir("libs")
                val incsDir = libNameDir.dir("include")
                libsDir.asFile.mkdirs()
                incsDir.asFile.mkdirs()
                println("generate => libsDir incsDir")
                //拷贝头文件目录
                moduleConfigExt.includeDir?.get()?.let { sourceDir ->
                    sourceDir.asFile.copyRecursively(incsDir.asFile, true)
                } ?: let {
                    error("includeDir is null")
                }
                //拷贝库目录
                val targetLibraryDir = libsDir.dir("android.$abiName")
                targetLibraryDir.asFile.mkdirs()
                println("generate => android.$abiName")

                val targetLibraryName = moduleConfigExt.libraryName ?: libName
                moduleConfigExt.libsDir?.get()?.dir(abiName)?.file(targetLibraryName)?.let { sourceLibraryFile ->
                    sourceLibraryFile.asFile.copyTo(targetLibraryDir.file(targetLibraryName).asFile)
                } ?: let {
                    error("libsDir is null")
                }
                //生成abi.json文件
                val abiJson = PrefabAbi().apply {
                    this.abi = abiName
                    this.api = moduleConfigExt.apiVersion
                    this.ndk = moduleConfigExt.ndkVersion
                    this.static = moduleConfigExt.static
                    this.stl = if (static) "c++_static" else "c++_shared"
                }
                val abiFormatResult = JsonUtils.jsonFormat(abiJson)
                targetLibraryDir.file("abi.json").asFile.writeText(abiFormatResult)
                //生成module.json文件
                val moduleJson = PrefabModule().apply {
                    this.android.library_name = libName
                }
                val moduleFormatResult = JsonUtils.jsonFormat(moduleJson)
                libNameDir.file("module.json").asFile.writeText(moduleFormatResult)
            }
        }
    }

}
