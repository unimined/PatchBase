package xyz.wagyourtail.patchbase.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

class PatchCreatorPlugin : Plugin<Project> {
    val pluginVersion: String = PatchCreatorPlugin::class.java.`package`.implementationVersion ?: "unknown"

    override fun apply(target: Project) {
        target.logger.lifecycle("[PatchbaseCreator] Plugin Version: $pluginVersion")

        target.extensions.create("patchbase", PatchExtension::class.java)
    }
}
