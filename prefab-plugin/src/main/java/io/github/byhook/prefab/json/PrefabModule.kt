package io.github.byhook.prefab.json

class PrefabModule {

    var library_name: String = ""

    var export_libraries: List<String> = mutableListOf()

    val android = PrefabAndroidData()

}
