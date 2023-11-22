package xyz.wagyourtail.patchbase.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
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

fun MinecraftConfig.patchBaseCreator() {
    if (side == EnvType.COMBINED) {
        project.logger.warn("[PatchBase/Creator ${this.project.path} ${sourceSet}] Merged may make applying patches more difficult, proceed with caution")
    }
    if (!defaultRemapJar) {
        project.logger.warn("[PatchBase/Creator ${this.project.path} ${sourceSet}] defaultRemapJar is false, this may cause issues with patching")
    }
    if (mcPatcher !is JarModAgentMinecraftTransformer) {
        project.logger.warn("[PatchBase/Creator ${this.project.path} ${sourceSet}] mcPatcher is not a JarModAgentMinecraftTransformer, this may cause issues with dev runs")
    }

    project.tasks.register("createSourcePatch".withSourceSet(sourceSet), CreateSourcePatchTask::class.java) {
        it.group = "patchbase"
        it.sourceDir.set(project.file("src/main/java"))
        it.outputDir.set(project.file("patches/main"))
        val sourceFile = minecraftFileDev.resolveSibling(minecraftFileDev.nameWithoutExtension + "-sources.jar")
        it.sources.set(project.files(sourceFile))
        if (!sourceFile.exists()) {
            it.dependsOn("genSources")
        }
    }

    project.tasks.register("applySourcePatch".withSourceSet(sourceSet), ApplySourcePatchTask::class.java) {
        it.group = "patchbase"
        it.patchDir.set(project.file("patches/main"))
        it.outputDir.set(project.file("src/main/java"))
        val sourceFile = minecraftFileDev.resolveSibling(minecraftFileDev.nameWithoutExtension + "-sources.jar")
        it.sources.set(project.files(sourceFile))
        if (!sourceFile.exists()) {
            it.dependsOn("genSources".withSourceSet(sourceSet))
        }
    }

    project.tasks.register("createClassPatch".withSourceSet(sourceSet), CreateClassPatchTask::class.java) {
        it.group = "patchbase"
        it.inputFile.set((project.tasks.findByName("remapJar".withSourceSet(sourceSet)) as Jar).outputs.files.singleFile)

        when (side) {
            EnvType.CLIENT -> it.classpath.set(project.files(minecraftData.minecraftClientFile))
            EnvType.SERVER -> it.classpath.set(project.files(minecraftData.minecraftServerFile))
            EnvType.COMBINED -> {
                it.classpath.set(project.files(mergedOfficialMinecraftFile))
            }
            else -> throw IllegalStateException("Unknown side: $side")
        }

        it.archiveClassifier.set("patch")
        it.dependsOn("remapJar".withSourceSet(sourceSet))
    }
}