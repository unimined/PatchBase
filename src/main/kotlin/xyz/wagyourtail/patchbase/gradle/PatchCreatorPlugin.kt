package xyz.wagyourtail.patchbase.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar
import xyz.wagyourtail.patchbase.gradle.tasks.ApplySourcePatchTask
import xyz.wagyourtail.patchbase.gradle.tasks.CreateClassPatchTask
import xyz.wagyourtail.patchbase.gradle.tasks.CreateSourcePatchTask
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.internal.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.util.sourceSets

class PatchCreatorPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        target.tasks.register("createSourcePatch", CreateSourcePatchTask::class.java) {
            it.group = "patchbase"
            it.sourceDir.set(target.file("src/main/java"))
            it.outputDir.set(target.file("patches/main"))
            val mc = target.unimined.minecrafts[target.sourceSets.named("main").get()].minecraftFileDev
            val sourceFile = mc.resolveSibling(mc.nameWithoutExtension + "-sources.jar")
            it.sources.set(target.files(sourceFile))
            if (!sourceFile.exists()) {
                it.dependsOn("genSources")
            }
        }
        target.tasks.register("applySourcePatch", ApplySourcePatchTask::class.java) {
            it.group = "patchbase"
            it.patchDir.set(target.file("patches/main"))
            it.outputDir.set(target.file("src/main/java"))
            val mc = target.unimined.minecrafts[target.sourceSets.named("main").get()].minecraftFileDev
            val sourceFile = mc.resolveSibling(mc.nameWithoutExtension + "-sources.jar")
            it.sources.set(target.files(sourceFile))
            if (!sourceFile.exists()) {
                it.dependsOn("genSources")
            }
        }
        target.tasks.register("createClassPatch", CreateClassPatchTask::class.java) {
            it.group = "patchbase"
            it.inputFile.set((target.tasks.findByName("remapJar") as Jar).outputs.files.singleFile)
            val mc = target.unimined.minecrafts[target.sourceSets.named("main").get()]
            when (mc.side) {
                EnvType.CLIENT -> it.classpath.set(target.files(mc.minecraftData.minecraftClientFile))
                EnvType.SERVER -> it.classpath.set(target.files(mc.minecraftData.minecraftServerFile))
                EnvType.COMBINED -> it.classpath.set(target.files(mc.mergedOfficialMinecraftFile))
                else -> throw IllegalStateException("Unknown side: ${mc.side}")
            }
            it.archiveClassifier.set("patch")
            it.dependsOn("remapJar")

        }
    }

}
