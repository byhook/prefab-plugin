package io.github.byhook.prefab.utils

import com.google.gson.GsonBuilder


object JsonUtils {

    fun jsonFormat(target: Any): String {
        return GsonBuilder().setPrettyPrinting().create().toJson(target)
    }

}