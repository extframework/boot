package dev.extframework.boot.test.loader

import dev.extframework.boot.loader.*
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import java.nio.ByteBuffer
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.Test


fun  newClassBytes(name: String): ByteBuffer {
    val node = ClassNode()
    node.name = name.replace('.', '/')
    node.superName = "java/lang/Object"
    node.access = Opcodes.ACC_PUBLIC
    node.version = 61

    val writer = ClassWriter(0)
    node.accept(writer)
    return ByteBuffer.wrap(writer.toByteArray())
}

class TestIntegratedLoaderConcurrency {


    fun concurrentlyLoadClasses(
        loaderProvider: (
            SourceProvider
        ) -> ClassLoader
    ) {
        val sources = object : SourceProvider {
            override val packages: Set<String> = setOf("com.example")

            override fun findSource(name: String): ByteBuffer? {
                println("Getting class source : '$name' in thread : '${Thread.currentThread().name}'")

                return if (!name.startsWith("java")) newClassBytes(name) else null
            }
        }

        // URLs pointing to classpath
        val classLoader = loaderProvider(sources)

        // Create a pool of threads
        val executorService = Executors.newFixedThreadPool(10)

        // Define tasks to load classes
        val task1 = Callable { classLoader.loadClass("com.example.Class1") }
        val task2 = Callable { classLoader.loadClass("com.example.Class1") }
        val task3 = Callable { classLoader.loadClass("com.example.Class1") }

        // Submit tasks
        val future1 = executorService.submit(task1)
        val future2 = executorService.submit(task2)
        val future3 = executorService.submit(task3)

        // Wait for all tasks to complete and assert no exceptions occurred
        try {
            println(future1.get())
            println(future2.get())
            println(future3.get())
            assert(true) // All classes loaded without exception
        } catch (e: Exception) {
            throw e
        } finally {
            executorService.shutdown()
        }
    }


    @Test
    fun `Test raw integrated loader`() {
        concurrentlyLoadClasses {
            IntegratedLoader(
                name = "Loader A",
                sourceProvider = it,
                parent = ClassLoader.getPlatformClassLoader()
            )
        }
    }

    @Test
    fun `Test mutable integrated loader`() {
        concurrentlyLoadClasses {
            MutableClassLoader(
                name = "Mutable Loader A",
                mutableListOf(it),
                parent = ClassLoader.getPlatformClassLoader()
            )
        }
    }
}