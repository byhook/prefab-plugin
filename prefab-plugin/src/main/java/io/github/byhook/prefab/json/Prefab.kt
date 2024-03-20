package io.github.byhook.prefab.json

class Prefab {
    /**
     * 指定prefab的版本
     */
    var schema_version = 2

    /**
     * 库的名称
     */
    var name: String? = null

    /**
     * 库的版本号
     */
    var version: String? = null

    /**
     * 依赖列表
     */
    var dependencies: List<String> = mutableListOf()

}
