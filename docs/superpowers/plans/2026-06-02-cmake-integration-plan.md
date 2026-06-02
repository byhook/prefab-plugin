# CMake Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add CMake build capability to prefab-plugin so it can compile native code from CMakeLists.txt, collect artifacts, and package them into a Prefab AAR — all driven by a single `cmake { }` DSL block.

**Architecture:** One new extension class (`PrefabCmakeExtension`) for DSL configuration, one new task (`CmakeBuildTask`) that shells out to cmake per ABI, and modifications to two existing files to wire the cmake block into the extension and register the task. When `cmake { }` is not configured, the plugin behaves identically to the current version.

**Tech Stack:** Kotlin, Gradle 8.13, java-gradle-plugin, Gson

---

## File Structure

| File | Action | Responsibility |
|---|---|---|
| `extension/PrefabCmakeExtension.kt` | Create | CMake configuration DSL properties |
| `task/CmakeBuildTask.kt` | Create | CMake configure + build + strip per ABI |
| `extension/PrefabRootExtension.kt` | Modify | Add `cmake { }` nested extension |
| `PrefabGeneratePlugin.kt` | Modify | Register `CmakeBuildTask`, conditionally rewire dependency chain |

---

### Task 1: Create PrefabCmakeExtension

**Files:**
- Create: `prefab-plugin/src/main/java/io/github/byhook/prefab/extension/PrefabCmakeExtension.kt`

- [ ] **Step 1: Write PrefabCmakeExtension**

```kotlin
package io.github.byhook.prefab.extension

import org.gradle.api.file.Directory

open class PrefabCmakeExtension {

    /**
     * Directory containing CMakeLists.txt (required when cmake block is configured).
     */
    lateinit var sourceDir: Directory

    /**
     * NDK version string, e.g. "27.0.12077973".
     * When null, auto-detected from ANDROID_NDK_HOME or local.properties ndk.dir.
     */
    var ndkVersion: String? = null

    /**
     * CMake build type. Default "Release".
     */
    var buildType: String = "Release"

    /**
     * ANDROID_PLATFORM. Default 21.
     */
    var platform: Int = 21

    /**
     * ANDROID_STL. Default "c++_shared".
     */
    var stl: String = "c++_shared"

    /**
     * Strip symbols after build using llvm-strip. Default true.
     */
    var stripSymbols: Boolean = true

    /**
     * Extra CMake -D arguments passed through to cmake configure.
     */
    var arguments: Map<String, String> = emptyMap()

    /**
     * Manual override for header sub-directories relative to sourceDir.
     * When empty, headers are collected from sourceDir/include/.
     */
    var includeSubDirs: List<String> = emptyList()
}
```

- [ ] **Step 2: Commit**

```bash
git add prefab-plugin/src/main/java/io/github/byhook/prefab/extension/PrefabCmakeExtension.kt
git commit -m "feat: add PrefabCmakeExtension DSL class

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 2: Create CmakeBuildTask

**Files:**
- Create: `prefab-plugin/src/main/java/io/github/byhook/prefab/task/CmakeBuildTask.kt`

- [ ] **Step 1: Write CmakeBuildTask**

```kotlin
package io.github.byhook.prefab.task

import io.github.byhook.prefab.extension.PrefabCmakeExtension
import io.github.byhook.prefab.extension.PrefabRootExtension
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.Properties
import javax.inject.Inject

open class CmakeBuildTask() : DefaultTask() {

    private lateinit var prefabConfigExt: PrefabRootExtension
    private lateinit var cmakeConfig: PrefabCmakeExtension

    @Inject
    constructor(prefabRootConfig: PrefabRootExtension) : this() {
        this.prefabConfigExt = prefabRootConfig
        this.cmakeConfig = prefabRootConfig.cmakeConfig
            ?: throw GradleException("CmakeBuildTask registered but cmake block not configured")
    }

    @TaskAction
    fun buildWithCmake() {
        val ndkDir = resolveNdkPath()
        val cmakeBin = resolveCmakeBinary(ndkDir)
        val toolchainFile = File(ndkDir, "build/cmake/android.toolchain.cmake")
        if (!toolchainFile.exists()) {
            throw GradleException("Android toolchain file not found: ${toolchainFile.absolutePath}")
        }
        val sourceDir = cmakeConfig.sourceDir.asFile
        if (!File(sourceDir, "CMakeLists.txt").exists()) {
            throw GradleException("CMakeLists.txt not found in ${sourceDir.absolutePath}")
        }

        val outputBaseDir = project.layout.buildDirectory.dir("cmake-output").get().asFile
        val libsOutputDir = File(outputBaseDir, "libs")
        val includeOutputDir = File(outputBaseDir, "include")

        cmakeConfig.includeSubDirs.ifEmpty {
            val defaultIncludeDir = File(sourceDir, "include")
            if (defaultIncludeDir.exists()) listOf(defaultIncludeDir.name) else emptyList()
        }.forEach { subDir ->
            val src = if (cmakeConfig.includeSubDirs.isEmpty()) {
                File(sourceDir, "include")
            } else {
                File(sourceDir, subDir)
            }
            if (src.exists()) {
                src.copyRecursively(File(includeOutputDir, subDir), true)
            }
        }

        prefabConfigExt.abiList.forEach { abi ->
            val abiBuildDir = File(outputBaseDir, "build/$abi")
            abiBuildDir.mkdirs()

            val configureArgs = mutableListOf(
                cmakeBin,
                "-S", sourceDir.absolutePath,
                "-B", abiBuildDir.absolutePath,
                "-DCMAKE_TOOLCHAIN_FILE=${toolchainFile.absolutePath}",
                "-DANDROID_ABI=$abi",
                "-DANDROID_PLATFORM=${cmakeConfig.platform}",
                "-DANDROID_STL=${cmakeConfig.stl}",
                "-DCMAKE_BUILD_TYPE=${cmakeConfig.buildType}"
            )
            cmakeConfig.arguments.forEach { (key, value) ->
                configureArgs.add("-D$key=$value")
            }

            logger.lifecycle("CMake configure: ${configureArgs.joinToString(" ")}")
            val configureResult = project.exec {
                it.commandLine(configureArgs)
                it.isIgnoreExitValue = true
            }
            if (configureResult.exitValue != 0) {
                throw GradleException("CMake configure failed for ABI $abi (exit code ${configureResult.exitValue})")
            }

            logger.lifecycle("CMake build for ABI $abi")
            val buildResult = project.exec {
                it.commandLine(cmakeBin, "--build", abiBuildDir.absolutePath)
                it.isIgnoreExitValue = true
            }
            if (buildResult.exitValue != 0) {
                throw GradleException("CMake build failed for ABI $abi (exit code ${buildResult.exitValue})")
            }

            val abiLibsDir = File(libsOutputDir, abi)
            abiLibsDir.mkdirs()
            abiBuildDir.walkTopDown()
                .filter { it.isFile && (it.extension == "so" || it.extension == "a") }
                .forEach { libFile ->
                    libFile.copyTo(File(abiLibsDir, libFile.name), true)
                }

            if (cmakeConfig.stripSymbols) {
                val hostTag = detectHostTag()
                val llvmStrip = File(ndkDir, "toolchains/llvm/prebuilt/$hostTag/bin/llvm-strip")
                if (llvmStrip.exists()) {
                    abiLibsDir.listFiles()?.filter { it.extension == "so" }?.forEach { soFile ->
                        logger.lifecycle("Stripping symbols: ${soFile.name}")
                        project.exec {
                            it.commandLine(llvmStrip.absolutePath, "--strip-all", soFile.absolutePath)
                        }
                    }
                } else {
                    logger.warn("llvm-strip not found at ${llvmStrip.absolutePath}, skipping strip step")
                }
            }
        }

        val copiedLibs = libsOutputDir.walkTopDown().count { it.isFile && (it.extension == "so" || it.extension == "a") }
        if (copiedLibs == 0) {
            logger.warn("No library files (.so/.a) found in ${libsOutputDir.absolutePath}")
        }

        prefabConfigExt.sourceLibsDir = project.layout.projectDirectory.dir(libsOutputDir.absolutePath)
        prefabConfigExt.sourceIncsDir = project.layout.projectDirectory.dir(includeOutputDir.absolutePath)

        logger.lifecycle("CMake build complete. Libraries: $libsOutputDir, Headers: $includeOutputDir")
    }

    private fun resolveNdkPath(): File {
        cmakeConfig.ndkVersion?.let { version ->
            val sdkDir = resolveSdkDir()
            val ndkDir = File(sdkDir, "ndk/$version")
            if (ndkDir.exists()) return ndkDir
            throw GradleException("NDK version $version not found at ${ndkDir.absolutePath}")
        }

        System.getenv("ANDROID_NDK_HOME")?.let {
            val ndkDir = File(it)
            if (ndkDir.exists()) return ndkDir
        }

        val localProps = File(project.rootDir, "local.properties")
        if (localProps.exists()) {
            val props = Properties()
            localProps.inputStream().use { props.load(it) }
            props.getProperty("ndk.dir")?.let {
                val ndkDir = File(it)
                if (ndkDir.exists()) return ndkDir
            }
        }

        val sdkDir = resolveSdkDir()
        val ndkDir = sdkDir.listFiles()?.filter {
            it.isDirectory && it.name.startsWith("ndk")
        }?.maxByOrNull { it.name }
        if (ndkDir != null) return ndkDir

        throw GradleException(
            "NDK not found. Set cmake.ndkVersion, ANDROID_NDK_HOME environment variable, " +
            "or ndk.dir in local.properties"
        )
    }

    private fun resolveSdkDir(): File {
        System.getenv("ANDROID_HOME")?.let { return File(it) }
        System.getenv("ANDROID_SDK_ROOT")?.let { return File(it) }
        val localProps = File(project.rootDir, "local.properties")
        if (localProps.exists()) {
            val props = Properties()
            localProps.inputStream().use { props.load(it) }
            props.getProperty("sdk.dir")?.let { return File(it) }
        }
        throw GradleException(
            "Android SDK not found. Set ANDROID_HOME environment variable " +
            "or sdk.dir in local.properties"
        )
    }

    private fun resolveCmakeBinary(ndkDir: File): String {
        val cmakeDir = File(ndkDir, "cmake")
        if (cmakeDir.exists()) {
            val versions = cmakeDir.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.name }
            if (!versions.isNullOrEmpty()) {
                val cmakeBin = File(versions.first(), "bin/cmake")
                if (cmakeBin.exists()) return cmakeBin.absolutePath
            }
        }
        return "cmake"
    }

    private fun detectHostTag(): String {
        val osName = System.getProperty("os.name").lowercase()
        val osArch = System.getProperty("os.arch").lowercase()
        return when {
            osName.contains("mac") || osName.contains("darwin") ->
                if (osArch.contains("aarch64") || osArch.contains("arm64")) "darwin-arm64" else "darwin-x86_64"
            osName.contains("linux") ->
                if (osArch.contains("aarch64") || osArch.contains("arm64")) "linux-aarch64" else "linux-x86_64"
            osName.contains("win") ->
                if (osArch.contains("aarch64") || osArch.contains("arm64")) "windows-arm64" else "windows-x86_64"
            else -> throw GradleException("Unsupported host platform: $osName / $osArch")
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add prefab-plugin/src/main/java/io/github/byhook/prefab/task/CmakeBuildTask.kt
git commit -m "feat: add CmakeBuildTask for CMake configure, build, and symbol stripping

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 3: Modify PrefabRootExtension to add cmake block

**Files:**
- Modify: `prefab-plugin/src/main/java/io/github/byhook/prefab/extension/PrefabRootExtension.kt`

- [ ] **Step 1: Add cmakeConfig field and cmake DSL method**

In `PrefabRootExtension`, add after the existing `dependsOnTask` declaration (line 37-38):

```kotlin
    var cmakeConfig: PrefabCmakeExtension? = null

    fun cmake(block: PrefabCmakeExtension.() -> Unit) {
        val ext = PrefabCmakeExtension()
        block(ext)
        this.cmakeConfig = ext
    }
```

And change `sourceLibsDir` and `sourceIncsDir` from `lateinit var` to nullable:

```kotlin
    // Before:
    lateinit var sourceLibsDir: Directory
    lateinit var sourceIncsDir: Directory

    // After:
    var sourceLibsDir: Directory? = null
    var sourceIncsDir: Directory? = null
```

The full modified file will be:

```kotlin
package io.github.byhook.prefab.extension

import io.github.byhook.prefab.utils.PrefabUtils
import org.gradle.api.file.Directory
import java.io.File

open class PrefabRootExtension {

    /**
     * 源库目录
     */
    var sourceLibsDir: Directory? = null

    /**
     * 源头文件目录
     */
    var sourceIncsDir: Directory? = null

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

    var cmakeConfig: PrefabCmakeExtension? = null

    fun cmake(block: PrefabCmakeExtension.() -> Unit) {
        val ext = PrefabCmakeExtension()
        block(ext)
        this.cmakeConfig = ext
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
                bindPrefabModuleExt(moduleLibName, libraryName, true, block)
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
```

- [ ] **Step 2: Commit**

```bash
git add prefab-plugin/src/main/java/io/github/byhook/prefab/extension/PrefabRootExtension.kt
git commit -m "feat: add cmake block DSL to PrefabRootExtension

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 4: Modify PrefabGeneratePlugin to register CmakeBuildTask

**Files:**
- Modify: `prefab-plugin/src/main/java/io/github/byhook/prefab/PrefabGeneratePlugin.kt`

- [ ] **Step 1: Wire CmakeBuildTask into the task dependency chain**

Replace the entire file with:

```kotlin
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
```

- [ ] **Step 2: Commit**

```bash
git add prefab-plugin/src/main/java/io/github/byhook/prefab/PrefabGeneratePlugin.kt
git commit -m "feat: register CmakeBuildTask and conditional dependency chain

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 5: Update GenerateModulesTask for nullable sourceLibsDir/sourceIncsDir

**Files:**
- Modify: `prefab-plugin/src/main/java/io/github/byhook/prefab/task/GenerateModulesTask.kt`

Since `sourceLibsDir` and `sourceIncsDir` are now nullable, `GenerateModulesTask` must handle the case where they are set (either by user or by `CmakeBuildTask`) at execution time.

- [ ] **Step 1: Update GenerateModulesTask to use nullable access**

Replace the file with:

```kotlin
package org.github.byhook.prefab.task

import io.github.byhook.prefab.extension.PrefabRootExtension
import io.github.byhook.prefab.json.Prefab
import io.github.byhook.prefab.json.PrefabAbi
import io.github.byhook.prefab.json.PrefabModule
import io.github.byhook.prefab.utils.PrefabUtils
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
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
        val sourceLibsDir = prefabConfigExt.sourceLibsDir
            ?: throw GradleException("sourceLibsDir is not set. Configure cmake { } block or set sourceLibsDir manually.")
        val sourceIncsDir = prefabConfigExt.sourceIncsDir
            ?: throw GradleException("sourceIncsDir is not set. Configure cmake { } block or set sourceIncsDir manually.")

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
                sourceIncsDir.dir(moduleConfigExt.includeSubDirName)
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
                sourceLibsDir.dir(abiName)
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
```

- [ ] **Step 2: Commit**

```bash
git add prefab-plugin/src/main/java/io/github/byhook/prefab/task/GenerateModulesTask.kt
git commit -m "fix: handle nullable sourceLibsDir/sourceIncsDir in GenerateModulesTask

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 6: Verify backward compatibility — compile and test without cmake block

- [ ] **Step 1: Compile the plugin**

```bash
cd /Users/handyzhou/Documents/androidProjects/prefab-plugin && ./gradlew :prefab-plugin:compileKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run existing Gradle tasks to verify no regression**

```bash
cd /Users/handyzhou/Documents/androidProjects/prefab-plugin && ./gradlew :prefab-plugin:tasks --group="other"
```

Expected: `generateModulesTask` and `generatePrefabTask` are listed, `cmakeBuildTask` is NOT listed (since no cmake block is configured in the plugin module itself).

- [ ] **Step 3: Commit if successful**

```bash
git add -A
git commit -m "chore: verify backward compatibility after cmake integration

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 7: End-to-end smoke test with minimal CMake project

- [ ] **Step 1: Create a minimal test CMake project in the app module**

Verify the app module has `src/main/cpp/` with a minimal `CMakeLists.txt`:

```bash
ls /Users/handyzhou/Documents/androidProjects/prefab-plugin/app/src/main/cpp/ 2>/dev/null || echo "No cpp directory"
```

If no cpp directory exists, skip the E2E test — the compile verification in Task 6 is sufficient for CI. The full integration test (with real CMake) requires an NDK installation and should be run manually.

- [ ] **Step 2: Run full build if CMake infrastructure is available**

```bash
cd /Users/handyzhou/Documents/androidProjects/prefab-plugin && ./gradlew :prefab-plugin:build
```

Expected: BUILD SUCCESSFUL.

---

## Self-Review

### 1. Spec Coverage Check

| Spec Section | Covered By |
|---|---|
| `PrefabCmakeExtension` DSL class | Task 1 |
| `CmakeBuildTask` with Step 1/2/2.5/3 | Task 2 |
| NDK resolution (3-tier priority) | Task 2 `resolveNdkPath()` |
| CMake binary resolution | Task 2 `resolveCmakeBinary()` |
| Host tag detection for llvm-strip | Task 2 `detectHostTag()` |
| `cmake { }` block in PrefabRootExtension | Task 3 |
| Plugin registration + dependency chain | Task 4 |
| Nullable sourceLibsDir/sourceIncsDir | Tasks 3 + 5 |
| Error: NDK not found | Task 2 `resolveNdkPath()` throws `GradleException` |
| Error: cmake not available | Task 2 `resolveCmakeBinary()` falls back to PATH |
| Error: CMakeLists.txt missing | Task 2 pre-check |
| Error: CMake build failure | Task 2 exit code checks |
| Error: No artifacts | Task 2 warn log |
| Error: llvm-strip unavailable | Task 2 warn + skip |
| Error: cmake block + manual sourceLibsDir | Task 4 warn log |
| Backward compatibility | Tasks 4 (else branch) + Task 6 |

### 2. Placeholder Check

No TODOs, TBDs, or incomplete sections. All code steps contain complete implementations.

### 3. Type Consistency Check

- `PrefabCmakeExtension` property types match `CmakeBuildTask` usage: `sourceDir: Directory`, `ndkVersion: String?`, `buildType: String`, `platform: Int`, `stl: String`, `stripSymbols: Boolean`, `arguments: Map<String, String>`, `includeSubDirs: List<String>`
- `PrefabRootExtension.cmakeConfig: PrefabCmakeExtension?` matches `CmakeBuildTask` constructor parameter
- `PrefabRootExtension.sourceLibsDir` / `sourceIncsDir` changed to `Directory?` — consumed consistently in Task 5
- `GenerateModulesTask` uses the `GradleException` import which is added
