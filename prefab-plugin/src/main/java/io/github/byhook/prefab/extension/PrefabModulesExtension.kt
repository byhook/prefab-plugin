package io.github.byhook.prefab.extension

open class PrefabModulesExtension(
    val static: Boolean = false,
    //库名(无后缀名) libmp3lame
    val libraryName: String
) {

    var apiVersion: Int = 21

    var ndkVersion: Int = 25

}
