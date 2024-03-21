package io.github.byhook.prefab.utils

import com.google.gson.GsonBuilder


object PrefabUtils {

    fun jsonFormat(target: Any): String {
        return GsonBuilder().setPrettyPrinting().create().toJson(target)
    }

    fun getLibraryName(libraryName: String): String {
        return if (libraryName.startsWith("lib")) {
            libraryName
        } else {
            "lib$libraryName"
        }
    }

}