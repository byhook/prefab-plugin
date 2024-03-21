package io.github.byhook.prefab.task

import io.github.byhook.prefab.extension.PrefabRootExtension
import io.github.byhook.prefab.json.Prefab
import io.github.byhook.prefab.json.PrefabAbi
import io.github.byhook.prefab.json.PrefabModule
import io.github.byhook.prefab.utils.PrefabUtils
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

open class GenerateModulesTask() : DefaultTask() {

    private lateinit var prefabConfigExt: PrefabRootExtension

    private lateinit var prefabDir: Directory

    @Inject
    constructor (prefabRootConfig: PrefabRootExtension) : this() {
        this.prefabConfigExt = prefabRootConfig
        val targetDir = prefabRootConfig.prefabBuildDir.asFile
        val deleteResult = targetDir.deleteRecursively()
        println("delete prefab directory $deleteResult")
        val result = targetDir.mkdirs()
        println("mkdir prefab directory $result")
        prefabDir = prefabRootConfig.prefabBuildDir.dir("prefab")
        println("generate prefab directory")
    }

    @TaskAction
    fun generateModules() {
        val modulesDir = prefabDir.dir("modules")
        modulesDir.asFile.mkdirs()
        //1、生成prefab.json文件
        val prefab = Prefab().apply {
            this.name = prefabConfigExt.prefabName
            this.version = prefabConfigExt.prefabVersion
        }
        val result = PrefabUtils.jsonFormat(prefab)
        println("generate => prefab.json: $result")
        prefabDir.file("prefab.json").asFile.writeText(result)
        //2、拷贝AndroidManifest.xml清单文件
        prefabConfigExt.manifestFile.copyTo(prefabConfigExt.prefabBuildDir
            .file(prefabConfigExt.manifestFile.name).asFile)
        println("generate => ${prefabConfigExt.manifestFile.absolutePath}")
        //3、遍历ABI列表
        prefabConfigExt.abiList.forEach { abiName ->
            prefabConfigExt.prefabModulesMap.forEach {
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
                prefabConfigExt.sourceIncsDir.dir(moduleConfigExt.includeSubDirName)
                    .asFile.copyRecursively(
                    incsDir.dir(moduleConfigExt.includeSubDirName).asFile,
                    true
                )
                //拷贝库目录
                val targetLibraryDir = libsDir.dir("android.$abiName")
                targetLibraryDir.asFile.mkdirs()
                println("generate => android.$abiName")
                //例如libmp3lame.so
                val extensionName = if (moduleConfigExt.static) ".a" else ".so"
                val libraryFileName = "${moduleConfigExt.libraryName}$extensionName"
                println("generate => libraryFileName:$libraryFileName")
                prefabConfigExt.sourceLibsDir.dir(abiName)
                    .file(libraryFileName)
                    .asFile
                    .copyTo(targetLibraryDir.file(libraryFileName).asFile)
                //生成abi.json文件
                val abiJson = PrefabAbi().apply {
                    this.abi = abiName
                    this.api = moduleConfigExt.apiVersion
                    this.ndk = moduleConfigExt.ndkVersion
                    this.static = moduleConfigExt.static
                    this.stl = if (static) "c++_static" else "c++_shared"
                }
                val abiFormatResult = PrefabUtils.jsonFormat(abiJson)
                targetLibraryDir.file("abi.json").asFile.writeText(abiFormatResult)
                //生成module.json文件
                val targetLibraryName = moduleConfigExt.libraryName
                val moduleJson = PrefabModule().apply {
                    this.library_name = targetLibraryName
                    this.android.library_name = targetLibraryName
                }
                val moduleFormatResult = PrefabUtils.jsonFormat(moduleJson)
                libNameDir.file("module.json").asFile.writeText(moduleFormatResult)
            }
        }
    }

}
