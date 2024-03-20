package io.github.byhook.prefab.json

class PrefabModule {

    var export_libraries: List<String> = mutableListOf()
    val android by lazy {
        AndroidData()
    }

    class AndroidData {
        var library_name: String = ""
        var export_libraries: List<String> = mutableListOf()
    }

}
