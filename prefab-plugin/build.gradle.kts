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

gradlePlugin {
    plugins {
        create("prefabPlugin") {
            group = "io.github.byhook"
            version = "1.0.56"
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