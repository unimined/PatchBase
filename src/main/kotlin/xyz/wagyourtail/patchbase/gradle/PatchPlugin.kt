package xyz.wagyourtail.patchbase.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

class PatchPlugin : Plugin<Project> {
    val pluginVersion: String = PatchPlugin::class.java.`package`.implementationVersion ?: "unknown"

    override fun apply(target: Project) {
        target.logger.lifecycle("[Patchbase] Plugin Version: $pluginVersion")

        target.extensions.create("patchbase", PatchExtension::class.java)
    }
}
