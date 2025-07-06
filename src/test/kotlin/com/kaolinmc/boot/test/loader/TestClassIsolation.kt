package com.kaolinmc.boot.test.loader

import com.kaolinmc.archives.Archives
import com.kaolinmc.boot.loader.ArchiveClassProvider
import com.kaolinmc.boot.loader.ArchiveSourceProvider
import com.kaolinmc.boot.loader.IntegratedLoader
import com.kaolinmc.boot.loader.SourceProvider
import com.kaolinmc.common.util.toBytes
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
            IntegratedLoader("Loader A", sourceProvider = sourceProvider, parent = ClassLoader.getSystemClassLoader())
        val classLoaderB =
            IntegratedLoader("Loader B", sourceProvider = sourceProvider, parent = ClassLoader.getSystemClassLoader())

        val classA = classLoaderA.loadClass(className)
        val classB = classLoaderB.loadClass(className)

        check(classA != classB)
    }

    @Test
    fun `make this fail`() {
        val loader = object : ClassLoader(this::class.java.classLoader) {
            override fun loadClass(name: String): Class<*> {
                val a = super.loadClass(name)

                if (name == "com.kaolinmc.boot.test.loader.TestClassIsolation") {
                    val newClassBytes = newClassBytes(name)
                    return defineClass(name, newClassBytes, ProtectionDomain(CodeSource(URL("https://google.com"), arrayOf<Certificate>()), Permissions()))
                }

                return a
            }
        }

        loader.loadClass("com.kaolinmc.boot.test.loader.TestClassIsolation")
    }
}