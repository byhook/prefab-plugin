package io.github.byhook.prefab.task

import io.github.byhook.prefab.extension.PrefabCmakeExtension
import io.github.byhook.prefab.extension.PrefabRootExtension
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.File
import java.util.Properties
import javax.inject.Inject

open class CmakeBuildTask() : DefaultTask() {

    private lateinit var prefabConfigExt: PrefabRootExtension
    private lateinit var cmakeConfig: PrefabCmakeExtension
    private lateinit var execOps: ExecOperations

    @Inject
    constructor(prefabRootConfig: PrefabRootExtension, execOps: ExecOperations) : this() {
        this.prefabConfigExt = prefabRootConfig
        this.cmakeConfig = prefabRootConfig.cmakeConfig
            ?: throw GradleException("CmakeBuildTask registered but cmake block not configured")
        this.execOps = execOps
    }

    @TaskAction
    fun buildWithCmake() {
        val ndkDir = resolveNdkPath()
        val cmakeBin = resolveCmakeBinary()
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
            val configureResult = execOps.exec {
                it.commandLine(configureArgs)
                it.isIgnoreExitValue = true
            }
            if (configureResult.exitValue != 0) {
                throw GradleException("CMake configure failed for ABI $abi (exit code ${configureResult.exitValue})")
            }

            logger.lifecycle("CMake build for ABI $abi")
            val buildResult = execOps.exec {
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
                        execOps.exec {
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

        collectHeaders(sourceDir, outputBaseDir, includeOutputDir)

        prefabConfigExt.sourceLibsDir = project.layout.projectDirectory.dir(libsOutputDir.absolutePath)
        prefabConfigExt.sourceIncsDir = project.layout.projectDirectory.dir(includeOutputDir.absolutePath)

        logger.lifecycle("CMake build complete. Libraries: $libsOutputDir, Headers: $includeOutputDir")
    }

    private fun collectHeaders(sourceDir: File, outputBaseDir: File, includeOutputDir: File) {
        if (cmakeConfig.headersDir != null) {
            cmakeConfig.headersDir!!.asFile.copyRecursively(includeOutputDir, true)
            return
        }

        val firstAbi = prefabConfigExt.abiList.firstOrNull() ?: return
        val firstAbiBuildDir = File(outputBaseDir, "build/$firstAbi")
        val depsDir = File(firstAbiBuildDir, "_deps")
        if (depsDir.exists()) {
            var foundHeaders = false
            depsDir.listFiles()?.filter { it.isDirectory }?.forEach { depDir ->
                val depIncludeDir = File(depDir, "include")
                if (depIncludeDir.exists()) {
                    depIncludeDir.copyRecursively(includeOutputDir, true)
                    foundHeaders = true
                    logger.lifecycle("Auto-discovered headers from: ${depIncludeDir.absolutePath}")
                }
            }
            if (foundHeaders) return
        }

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

    private fun resolveCmakeBinary(): String {
        cmakeConfig.cmakePath?.let { path ->
            val cmakeFile = File(path)
            if (cmakeFile.exists() && cmakeFile.canExecute()) return cmakeFile.absolutePath
        }
        val sdkDir = resolveSdkDir()
        val sdkCmakeDir = File(sdkDir, "cmake")
        if (sdkCmakeDir.exists()) {
            val versions = sdkCmakeDir.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.name }
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
