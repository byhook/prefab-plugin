plugins {
    id("java-gradle-plugin")
    alias(libs.plugins.jetbrainsKotlinJvm)
    id("maven-publish")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    implementation(libs.google.gson)
}

gradlePlugin {
    plugins {
        create("prefabPlugin") {
            group = project.property("artifact.group").toString()
            version = project.property("artifact.version").toString()
            id = "io.github.byhook.prefab"
            implementationClass = "io.github.byhook.prefab.PrefabGeneratePlugin"
        }
    }
}

publishing {
    repositories {
        maven {
            name = "repsyMaven"
            url = uri(
                project.findProperty("repsyMavenUrl") as String?
                    ?: ""
            )
            credentials {
                username = project.findProperty("repsyUsername") as String?
                    ?: ""
                password = project.findProperty("repsyMavenPassword") as String?
                    ?: ""
            }
        }
    }
    publications {
        withType<MavenPublication> {
            artifactId = project.property("artifact.name").toString()
        }
    }
}