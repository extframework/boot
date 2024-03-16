package net.yakclient.boot.archive

import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.job
import com.durganmcbroom.jobs.result
import com.durganmcbroom.resources.Resource
import com.durganmcbroom.resources.openStream
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

public fun moduleNameFor(artifact: Resource, name: String): Result<String> = result {
    val zip = ZipInputStream(artifact.openStream())

    var moduleName: String? = null
    var automaticName: String? = null

    var currentEntry = zip.nextEntry

    while (currentEntry != null) {
        if (currentEntry.name.contains("module-info.class")) moduleName = readModuleInfoName(zip)
        if (currentEntry.name.contains("MANIFEST.MF")) automaticName = readManifestName(zip)

        zip.closeEntry()
        currentEntry = zip.nextEntry
    }

    moduleName ?: automaticName ?: name.replace('-', '.')
}
