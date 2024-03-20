package io.github.byhook.prefab

import org.gradle.api.Plugin
import org.gradle.api.Project

class PrefabPackPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        println("apply prefab pack plugin!")
    }

}