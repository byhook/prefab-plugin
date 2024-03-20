package io.github.byhook.prefab.extension

import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider

open class PrefabModulesExtension {

    var libraryName: String? = null

    var apiVersion: Int = 21

    var ndkVersion: Int = 25

    var static: Boolean = false

    var libsDir: Provider<Directory>? = null

    var includeDir: Provider<Directory>? = null

}
