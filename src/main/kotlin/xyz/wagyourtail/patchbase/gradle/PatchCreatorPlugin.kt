package xyz.wagyourtail.patchbase.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.jvm.tasks.Jar
import xyz.wagyourtail.patchbase.gradle.tasks.ApplySourcePatchTask
import xyz.wagyourtail.patchbase.gradle.tasks.CreateClassPatchTask
import xyz.wagyourtail.patchbase.gradle.tasks.CreateSourcePatchTask
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.internal.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.internal.minecraft.patch.NoTransformMinecraftTransformer
import xyz.wagyourtail.unimined.internal.minecraft.patch.jarmod.JarModAgentMinecraftTransformer
import xyz.wagyourtail.unimined.util.sourceSets
import xyz.wagyourtail.unimined.util.withSourceSet

class PatchCreatorPlugin : Plugin<Project> {

    val pluginVersion: String = PatchCreatorPlugin::class.java.`package`.implementationVersion ?: "unknown"


    override fun apply(target: Project) {
        target.logger.lifecycle("[PatchbaseCreator] Plugin Version: $pluginVersion")

        target.extensions.create("patchbase", PatchExtension::class.java)
    }

}