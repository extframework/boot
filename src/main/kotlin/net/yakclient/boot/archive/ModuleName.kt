package net.yakclient.boot.archive

import net.yakclient.common.util.resource.SafeResource
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import java.io.InputStream
import java.util.*
import java.util.zip.ZipInputStream

public fun readModuleInfoName(moduleInfoIn: InputStream): String? {
    val reader = ClassReader(moduleInfoIn)
    val node = ClassNode()

    reader.accept(node, 0)

    return node.module?.name
}

public fun readManifestName(manifestIn: InputStream): String? {
    val properties = Properties().also { it.load(manifestIn) }

    return properties.getProperty("Automatic-Module-Name")
}

public fun moduleNameFor(artifact: SafeResource, name: String): String {
    val zip = ZipInputStream(artifact.open())

    var moduleName: String? = null
    var automaticName: String? = null

    var currentEntry = zip.nextEntry

    while (currentEntry != null) {
        if (currentEntry.name.contains("module-info.class")) moduleName = readModuleInfoName(zip)
        if (currentEntry.name.contains( "MANIFEST.MF")) automaticName = readManifestName(zip)

        zip.closeEntry()
        currentEntry = zip.nextEntry
    }

    return moduleName ?: automaticName ?: name.replace('-', '.')
}
