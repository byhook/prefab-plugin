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

    /**
     * Explicit path to the header directory to package into the prefab AAR.
     * When null, headers are auto-discovered from sourceDir/include/ or includeSubDirs.
     */
    var headersDir: Directory? = null

    /**
     * Explicit path to cmake binary.
     * When null, auto-detected from SDK/cmake/ or system PATH.
     */
    var cmakePath: String? = null
}
