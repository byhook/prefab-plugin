plugins {
    id("java-gradle-plugin")
    alias(libs.plugins.jetbrainsKotlinJvm)
    id("maven-publish")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(libs.google.gson)
}

val targetVersion = "1.0.23"

/**
 * jitpack.io的发布产物会变成这个
 * com.github.byhook:prefab-plugin
 * 可以在settings.gradle.kts配置这个
 * pluginManagement {
 *     resolutionStrategy {
 *         eachPlugin {
 *             if (requested.id.toString() == "io.github.byhook.prefab") {
 *                 useModule("com.github.byhook:prefab-plugin:${requested.version}")
 *             }
 *         }
 *     }
 * }
 */
gradlePlugin {
    plugins {
        create("prefabPlugin") {
            group = "io.github.byhook"
            version = targetVersion
            id = "io.github.byhook.prefab"
            implementationClass = "io.github.byhook.prefab.PrefabGeneratePlugin"
        }
    }
}