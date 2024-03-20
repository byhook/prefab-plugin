plugins {
    id("java-gradle-plugin")
    alias(libs.plugins.jetbrainsKotlinJvm)
    id("maven-publish")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

gradlePlugin {
    plugins {
        create("prefabPlugin") {
            group = "io.github.byhook"
            version = "1.0.0"
            id = "io.github.byhook.prefab"
            implementationClass = "io.github.byhook.prefab.PrefabPackPlugin" //PageAnalysisPlugin的全类名 取代resources声明
        }
    }
}

publishing {
    repositories {
        mavenLocal()
    }
}