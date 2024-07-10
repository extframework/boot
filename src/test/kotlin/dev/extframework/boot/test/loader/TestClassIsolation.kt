package dev.extframework.boot.test.loader

import dev.extframework.archives.Archives
import dev.extframework.boot.loader.ArchiveClassProvider
import dev.extframework.boot.loader.ArchiveSourceProvider
import dev.extframework.boot.loader.IntegratedLoader
import dev.extframework.boot.loader.SourceProvider
import dev.extframework.common.util.toBytes
import java.net.URL
import java.nio.ByteBuffer
import java.nio.file.Path
import java.security.BasicPermission
import java.security.CodeSource
import java.security.PermissionCollection
import java.security.Permissions
import java.security.ProtectionDomain
import java.security.cert.Certificate
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
    fun `make this fail`() {
        val loader = object : ClassLoader(this::class.java.classLoader) {
            override fun loadClass(name: String): Class<*> {
                val a = super.loadClass(name)

                if (name == "dev.extframework.boot.test.loader.TestClassIsolation") {
                    val newClassBytes = newClassBytes(name)
                    return defineClass(name, newClassBytes, ProtectionDomain(CodeSource(URL("https://google.com"), arrayOf<Certificate>()), Permissions()))
                }

                return a
            }
        }

        loader.loadClass("dev.extframework.boot.test.loader.TestClassIsolation")
    }

    @Test
    fun `Test module Isolation`() {
        val asmIn = TestClassIsolation::class.java.getResource("/blackbox-repository/org/ow2/asm/asm/9.7/asm-9.7.jar")!!.toURI().let(Path::of)

        val refA = Archives.find(
            asmIn,
            Archives.Finders.JPM_FINDER
        )
        val loaderA = IntegratedLoader(
            "Loader A",
            sourceProvider = ArchiveSourceProvider(refA),
            parent = ClassLoader.getPlatformClassLoader()
        )
        val archiveA = Archives.resolve(
            refA,
            loaderA,
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
                parent = loaderA
            ),
            Archives.Resolvers.JPM_RESOLVER,
            setOf(archiveA),
        ).archive

        archiveA.classloader.loadClass("org.objectweb.asm.ClassReader")
            .getConstructor(String::class.java).newInstance("java.lang.Object")
        archiveB.classloader.loadClass("org.objectweb.asm.ClassReader")
            .getConstructor(String::class.java).newInstance("java.lang.Object")

        println("asdf")
    }
}