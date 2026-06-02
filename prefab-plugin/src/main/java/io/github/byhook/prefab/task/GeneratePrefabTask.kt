package io.github.byhook.prefab.task

import io.github.byhook.prefab.extension.PrefabRootExtension
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

open class GeneratePrefabTask() : DefaultTask() {

    private lateinit var prefabConfigExt: PrefabRootExtension

    @Inject
    constructor(prefabConfigExt: PrefabRootExtension) : this() {
        this.prefabConfigExt = prefabConfigExt
    }

    @TaskAction
    fun createArchive() {
        val artifactDir = prefabConfigExt.prefabArtifactDir.asFile
        artifactDir.mkdirs()
        val archiveName = "${prefabConfigExt.prefabName}-${prefabConfigExt.prefabVersion}.aar"
        val destFile = File(artifactDir, archiveName)
        val sourceDir = prefabConfigExt.prefabBuildDir.asFile

        ZipOutputStream(FileOutputStream(destFile)).use { zos ->
            sourceDir.walkTopDown().filter { it.isFile }.forEach { file ->
                val entryName = file.relativeTo(sourceDir).path
                zos.putNextEntry(ZipEntry(entryName))
                FileInputStream(file).use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }

}
