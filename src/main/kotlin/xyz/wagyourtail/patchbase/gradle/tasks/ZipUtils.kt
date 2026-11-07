package xyz.wagyourtail.patchbase.gradle.tasks

import xyz.wagyourtail.patchbase.installer.PatchbaseInstaller
import java.io.InputStream
import java.nio.file.Path

/** Kotlin extension shims over [PatchbaseInstaller]'s static zip
 *  helpers — the task code's previous `unimined.util` call shape. */
fun <T> Path.readZipInputStreamFor(
    entry: String, throwIfMissing: Boolean, action: (InputStream) -> T
): T? = PatchbaseInstaller.readZipInputStreamFor(this, entry, throwIfMissing) {
    action(it)
}

fun Path.forEachInZip(action: (String, InputStream) -> Unit) =
    PatchbaseInstaller.forEachInZip(this) { name, stream -> action(name, stream) }
