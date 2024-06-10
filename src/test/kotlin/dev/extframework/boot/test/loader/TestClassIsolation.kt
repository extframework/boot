package dev.extframework.boot.test.loader

import dev.extframework.archives.Archives
import dev.extframework.boot.loader.ArchiveClassProvider
import dev.extframework.boot.loader.ArchiveSourceProvider
import dev.extframework.boot.loader.IntegratedLoader
import dev.extframework.boot.loader.SourceProvider
import java.nio.ByteBuffer
import java.nio.file.Path
import kotlin.test.Ignore
import kotlin.test.Test

class TestClassIsolation {
    @Test
    fun `Test isolation with same parent`() {
        val className = "A Class"

        val sourceProvider = object : SourceProvider {
            override val packages: Set<String> = setOf("")

            override fun findSource(name: String): ByteBuffer? {
                return if (name == className) newClassBytes(className)
                else null
            }
        }

        val classLoaderA =
            IntegratedLoader("Loader A", sourceProvider = sourceProvider, parent = ClassLoader.getPlatformClassLoader())
        val classLoaderB =
            IntegratedLoader("Loader B", sourceProvider = sourceProvider, parent = ClassLoader.getPlatformClassLoader())

        val classA = classLoaderA.loadClass(className)
        val classB = classLoaderB.loadClass(className)

        check(classA != classB)
    }

    @Test
    @Ignore
    fun `Test module Isolation`() {
        val asmIn = TestClassIsolation::class.java.getResource("/blackbox-repository/org/ow2/asm/asm/9.7/asm-9.7.jar")!!.toURI().let(Path::of)
        val asmAnalysis = TestClassIsolation::class.java.getResource("/blackbox-repository/org/ow2/asm/asm-tree/9.7/asm-tree-9.7.jar")!!.toURI().let(Path::of)

        val refA = Archives.find(
            asmIn,
            Archives.Finders.JPM_FINDER
        )
        val archiveA = Archives.resolve(
            refA,
            IntegratedLoader(
                "Loader A",
                sourceProvider = ArchiveSourceProvider(refA),
                parent = ClassLoader.getPlatformClassLoader()
            ),
            Archives.Resolvers.JPM_RESOLVER,
            setOf(),
        ).archive

        val refB = Archives.find(
            asmIn,
            Archives.Finders.JPM_FINDER
        )
        val archiveB = Archives.resolve(
            refA,
            IntegratedLoader(
                "Loader B",
                sourceProvider = ArchiveSourceProvider(refB),
                parent = ClassLoader.getPlatformClassLoader()
            ),
            Archives.Resolvers.JPM_RESOLVER,
            setOf(archiveA),
        ).archive

        val refC = Archives.find(
            asmAnalysis,
            Archives.Finders.JPM_FINDER
        )
        val archiveC = Archives.resolve(
            refC,
            IntegratedLoader("Loader C", sourceProvider = ArchiveSourceProvider(refC), classProvider = ArchiveClassProvider(archiveB), parent = ClassLoader.getPlatformClassLoader()),
            Archives.Resolvers.JPM_RESOLVER,
            setOf(archiveA)
        ).archive

//        val configA = ModuleLayer.boot().configuration().resolve(finderA, ModuleFinder.of(), setOf("org.objectweb.asm"))
//        val layerA = ModuleLayer.boot().defineModulesWithManyLoaders(
//            configA,
//            IntegratedLoader("Loader A", parent = ClassLoader.getPlatformClassLoader())
//        )
//
//        val finderB = ModuleFinder.of(pathIn)
//
//        val configB = ModuleLayer.boot().configuration().resolve(finderB, ModuleFinder.of(), setOf("org.objectweb.asm"))
//        val layerB = ModuleLayer.boot().defineModulesWithManyLoaders(
//            configB,
//            IntegratedLoader("Loader A", parent = ClassLoader.getPlatformClassLoader())
//        )

        archiveC.classloader.loadClass("org.objectweb.asm.tree.ClassNode")
        println("asdf")
    }
}