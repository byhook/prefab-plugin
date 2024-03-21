
## 生成prefab预构建库的插件
Google官方提供了预构建库功能
https://google.github.io/prefab/

因为有很多第三方库，是通过交叉编译，最终生成的libs和include
需要对这些生成的库和头文件按照prefab预构建库的规范，生成最终的aar文件

## 添加依赖配置
*1、配置jitpack.io的仓库地址*
```
pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.toString() == "io.github.byhook.prefab") {
                useModule("com.github.byhook:prefab-plugin:${requested.version}")
            }
        }
    }
    repositories {
        maven { url = uri("https://jitpack.io") }
        ...
    }
}
```

*2、在libs.versions.toml里配置版本信息*
```
[versions]
...
prefab = "1.0.1"
[libraries]
...
[plugins]
...
byhookPrefab = { id = "io.github.byhook.prefab", version.ref = "prefab" }
```
*3、配置插件相关依赖*
根目录配置插件依赖
```
plugins {
    ...
    alias(libs.plugins.byhookPrefab) apply false
}
```
库目录(例如：prefab-lame)配置插件id
```
plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    id("io.github.byhook.prefab")
    id("maven-publish")
}
```
*4、配置目标prefab相关配置*
```
generatePrefab {
    val rootBuildDir = rootProject.layout.buildDirectory
    //交叉编译生成的库目录
    sourceLibsDir = rootBuildDir.dir("libs").get()
    //交叉编译生成的头文件目录
    sourceIncsDir = rootBuildDir.dir("include").get()
    //生成预构建库的临时目录
    prefabBuildDir = rootBuildDir.dir("prefab-build").get()
    //最终打包完整的aar文件的产物目录
    prefabArtifactDir = rootBuildDir.dir("prefab-artifact").get()
    //指定预构建库的名字
    prefabName = "lame"
    //指定预构建库的版本号
    prefabVersion = "3.100.0"
    //预构建库支持abi的列表
    abiList = mutableListOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
    //生成的aar文件里包含的清单文件
    manifestFile = layout.projectDirectory
        .dir("src")
        .dir("main")
        .file("AndroidManifest.xml")
        .asFile
    //包含的库详情
    module("lame.so", false) {
        this.libraryName = "libmp3lame"
        this.libraryFileName = "libmp3lame.so"
    }
    module("lame.a", true) {
        this.libraryName = "libmp3lame"
        this.libraryFileName = "libmp3lame.a"
    }
}
```