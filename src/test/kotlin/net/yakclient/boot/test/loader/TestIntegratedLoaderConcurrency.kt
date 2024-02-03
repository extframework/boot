package net.yakclient.boot.test.loader

import net.yakclient.boot.loader.*
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import java.net.URL
import java.nio.ByteBuffer
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.Test


fun newClassBytes(name: String): ByteBuffer {
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
        val nameA = "A"
        val nameB = "B"

        val sources = object : SourceProvider {
            override val packages: Set<String> = setOf("")

            override fun findSource(name: String): ByteBuffer? {
                println("Getting class source : '$name' in thread : '${Thread.currentThread().name}'")

                // Make sure we hit this right
                Thread.sleep(1000)


                return when (name) {
                    nameA, nameB -> newClassBytes(name)
                    else -> null
                }
            }

        }

        val loader = loaderProvider(sources)

        val pool = Executors.newCachedThreadPool()

        val classA = pool.submit(Callable {
            Thread.sleep(1000)
            loader.loadClass(nameA)
        })
        val classB = pool.submit(Callable {
            Thread.sleep(1000)
            loader.loadClass(nameB)
        })

        while(true) {
            if (classA.isDone && classB.isDone) break
            Thread.sleep(5)
        }

        println(classA.get())
        println(classB.get())
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
                MutableSourceProvider(mutableListOf(it)),
                MutableClassProvider(mutableListOf()),
                parent = ClassLoader.getPlatformClassLoader()
            )
        }
    }
}