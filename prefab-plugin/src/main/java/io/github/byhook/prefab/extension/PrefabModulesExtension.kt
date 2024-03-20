package io.github.byhook.prefab.extension

import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider

open class PrefabModulesExtension(val static: Boolean = false) {

    /**
     * 完整文件名：
     * libmp3lame.so
     */
    var libraryFileName: String? = null

    /**
     * 库名(无后缀名)
     * libmp3lame
     */
    var libraryName: String? = null

    var apiVersion: Int = 21

    var ndkVersion: Int = 25

    var libsDir: Provider<Directory>? = null

    var includeDir: Provider<Directory>? = null

}
