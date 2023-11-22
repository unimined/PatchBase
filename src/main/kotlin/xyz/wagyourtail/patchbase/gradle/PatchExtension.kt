package xyz.wagyourtail.patchbase.gradle

import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.configurationcache.extensions.capitalized
import org.gradle.jvm.tasks.Jar
import xyz.wagyourtail.patchbase.gradle.tasks.ApplySourcePatchTask
import xyz.wagyourtail.patchbase.gradle.tasks.CreateClassPatchTask
import xyz.wagyourtail.patchbase.gradle.tasks.CreateSourcePatchTask
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.internal.minecraft.patch.jarmod.JarModAgentMinecraftTransformer
import xyz.wagyourtail.unimined.util.withSourceSet

abstract class PatchExtension(val project: Project) {

    fun patchBaseCreator(sourceSet: SourceSet) {
        val mc = project.unimined.minecrafts.map[sourceSet]!!
        if (mc.side == EnvType.COMBINED) {
            project.logger.warn("[PatchBase/Creator ${this.project.path} ${sourceSet}] Merged may make applying patches more difficult, proceed with caution")
        }
        if (!mc.defaultRemapJar) {
            project.logger.warn("[PatchBase/Creator ${this.project.path} ${sourceSet}] defaultRemapJar is false, this may cause issues with patching")
        }
        if (mc.mcPatcher !is JarModAgentMinecraftTransformer) {
            project.logger.warn("[PatchBase/Creator ${this.project.path} ${sourceSet}] mcPatcher is not a JarModAgentMinecraftTransformer, this may cause issues with dev runs")
        }

        project.tasks.register("createSourcePatch".withSourceSet(sourceSet), CreateSourcePatchTask::class.java) {
            it.group = "patchbase"
            it.sourceDir.set(project.file("src/${sourceSet.name}/java"))
            it.outputDir.set(project.file("patches/${sourceSet.name}"))
            val sourceFile = mc.minecraftFileDev.resolveSibling(mc.minecraftFileDev.nameWithoutExtension + "-sources.jar")
            it.sources.set(project.files(sourceFile))
            if (!sourceFile.exists()) {
                it.dependsOn("genSources")
            }
        }

        project.tasks.register("applySourcePatch".withSourceSet(sourceSet), ApplySourcePatchTask::class.java) {
            it.group = "patchbase"
            it.patchDir.set(project.file("patches/${sourceSet.name}"))
            it.outputDir.set(project.file("src/${sourceSet.name}/java"))
            val sourceFile = mc.minecraftFileDev.resolveSibling(mc.minecraftFileDev.nameWithoutExtension + "-sources.jar")
            it.sources.set(project.files(sourceFile))
            if (!sourceFile.exists()) {
                it.dependsOn("genSources".withSourceSet(sourceSet))
            }
        }

        project.tasks.register("createClassPatch".withSourceSet(sourceSet), CreateClassPatchTask::class.java) {
            it.group = "patchbase"
            it.inputFile.set((project.tasks.findByName("remap" + "jar".withSourceSet(sourceSet).capitalized()) as Jar).outputs.files.singleFile)

            when (mc.side) {
                EnvType.CLIENT -> it.classpath.set(project.files(mc.minecraftData.minecraftClientFile))
                EnvType.SERVER -> it.classpath.set(project.files(mc.minecraftData.minecraftServerFile))
                EnvType.COMBINED -> {
                    it.classpath.set(project.files(mc.mergedOfficialMinecraftFile))
                }
                else -> throw IllegalStateException("Unknown side: ${mc.side}")
            }

            it.archiveClassifier.set("patch")
            it.dependsOn("remap" + "jar".withSourceSet(sourceSet).capitalized())
        }
    }

    fun patchBase(minecraftConfig: MinecraftConfig) {
        minecraftConfig.patchBase()
    }

}