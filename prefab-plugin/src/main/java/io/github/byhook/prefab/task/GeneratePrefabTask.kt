package io.github.byhook.prefab.task

import io.github.byhook.prefab.extension.PrefabRootExtension
import org.gradle.api.tasks.bundling.Zip
import javax.inject.Inject

open class GeneratePrefabTask() : Zip() {

    private companion object {
        private const val AAR_EXTENSION = "aar"
    }

    @Inject
    constructor (prefabConfigExt: PrefabRootExtension) : this() {
        archiveBaseName.set(prefabConfigExt.prefabName)
        archiveVersion.set(prefabConfigExt.prefabVersion)
        archiveExtension.set(AAR_EXTENSION)
        this.from(project.files(prefabConfigExt.prefabBuildDir))
        destinationDirectory.set(prefabConfigExt.prefabArtifactDir)
    }

}
