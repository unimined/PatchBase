package xyz.wagyourtail.patchbase.gradle

import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import xyz.wagyourtail.patchbase.gradle.tasks.ApplyClassPatchTask
import xyz.wagyourtail.patchbase.gradle.tasks.ApplySourcePatchTask
import xyz.wagyourtail.patchbase.gradle.tasks.CreateClassPatchTask
import xyz.wagyourtail.patchbase.gradle.tasks.CreateSourcePatchTask
import java.io.File

/**
 * Standalone `patchbase` DSL — generic patch-set create/apply task
 * wiring over arbitrary files/configurations. Unimined-aware env
 * wiring (patch jars on an environment's MC jar) lives in the
 * `unimined-adapter-patchbase` plugin instead.
 */
abstract class PatchExtension(val project: Project) {

    /** Default [CreateSourcePatchTask.diffContextSize] for tasks
     *  registered via this extension. */
    var diffContextSize: Int = 3

    /** Default [CreateSourcePatchTask.trimWhitespace] for tasks
     *  registered via this extension. */
    var trimWhitespace: Boolean = true

    /** Default [CreateClassPatchTask.patchFormat] — `binpatch` or
     *  `bdiff`. */
    var patchFormat: String = "binpatch"

    /** Register a `createClassPatch<Suffix>` [org.gradle.jvm.tasks.Jar]
     *  task: diffs {@code input} against {@code classpath} and emits a
     *  patch jar — `.binpatch` entries for changed classes plus
     *  verbatim copies of new/changed non-class entries. */
    fun createClassPatch(
        suffix: String,
        input: Provider<RegularFile>,
        classpath: FileCollection,
        configure: CreateClassPatchTask.() -> Unit = {}
    ) = project.tasks.register("createClassPatch$suffix", CreateClassPatchTask::class.java) {
        it.group = "patchbase"
        it.inputFile.set(input)
        it.classpath.set(project.files(classpath))
        it.patchFormat.set(patchFormat)
        it.archiveClassifier.set("patch")
        it.configure()
    }

    /** Register an `applyClassPatch<Suffix>` task: applies patch
     *  jars to {@code input} producing {@code output} — the forge-
     *  binpatch-style dev flow: patch sets give you the loader's
     *  classes on the base jar without its source or a recompile.
     *  Patches apply in [patchJars] order; `.binpatch` base checksums
     *  verify each applies to the jar it was built against. */
    fun applyClassPatch(
        suffix: String,
        input: Provider<RegularFile>,
        patchJars: FileCollection,
        output: Provider<RegularFile>,
        configure: ApplyClassPatchTask.() -> Unit = {}
    ) = project.tasks.register("applyClassPatch$suffix", ApplyClassPatchTask::class.java) {
        it.group = "patchbase"
        it.inputFile.set(input)
        it.patchJars.from(patchJars)
        it.outputFile.set(output)
        it.configure()
    }

    /** Register a `createSourcePatch<Suffix>` task: unified diffs of
     *  {@code sourceDir} against {@code sources} into {@code outputDir}. */
    fun createSourcePatch(
        suffix: String,
        sourceDir: Provider<File>,
        outputDir: Provider<File>,
        sources: FileCollection,
        configure: CreateSourcePatchTask.() -> Unit = {}
    ) = project.tasks.register("createSourcePatch$suffix", CreateSourcePatchTask::class.java) {
        it.group = "patchbase"
        it.sourceDir.set(sourceDir)
        it.outputDir.set(outputDir)
        it.sources.set(project.files(sources))
        it.trimWhitespace.set(trimWhitespace)
        it.diffContextSize.set(diffContextSize)
        it.configure()
    }

    /** Register an `applySourcePatch<Suffix>` task: applies unified
     *  diffs from {@code patchDir} onto {@code sources}, writing to
     *  {@code outputDir}. */
    fun applySourcePatch(
        suffix: String,
        patchDir: Provider<File>,
        outputDir: Provider<File>,
        sources: FileCollection,
        configure: ApplySourcePatchTask.() -> Unit = {}
    ) = project.tasks.register("applySourcePatch$suffix", ApplySourcePatchTask::class.java) {
        it.group = "patchbase"
        it.patchDir.set(patchDir)
        it.outputDir.set(outputDir)
        it.sources.set(project.files(sources))
        it.trimWhitespace.set(trimWhitespace)
        it.diffContextSize.set(diffContextSize)
        it.configure()
    }

}
