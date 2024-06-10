import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.SuccessfulJob
import dev.extframework.boot.BootInstance
import dev.extframework.boot.component.ComponentConfiguration
import dev.extframework.boot.component.ComponentFactory
import dev.extframework.boot.component.ComponentInstance
import dev.extframework.boot.component.artifact.SoftwareComponentDescriptor
import dev.extframework.boot.component.context.ContextNodeValue
import dev.extframework.boot.maven.MavenDependencyResolver
import dev.extframework.boot.new
import dev.extframework.boot.test.testBootInstance
import kotlin.test.Test

class BootTestTest {
    @Test
    fun `Test boot instance setup`() {
        class TestConfiguration : ComponentConfiguration
        class TestComponentInstance : ComponentInstance<TestConfiguration> {
            override fun start() : Job<Unit> {
                println("Started!")

                return SuccessfulJob {  }
            }

            override fun end() : Job<Unit> {
                println("Ended :)")

                return SuccessfulJob {  }
            }
        }

        class TestFactory(boot: BootInstance) : ComponentFactory<TestConfiguration, TestComponentInstance>(boot) {
            override fun parseConfiguration(value: ContextNodeValue): TestConfiguration {
                return TestConfiguration()
            }

            override fun new(configuration: TestConfiguration): TestComponentInstance {
                return TestComponentInstance()
            }
        }

        val testDescriptor = SoftwareComponentDescriptor(
            "org.example",
            "example",
            "1.0", null
        )

        val bInstance = testBootInstance(
            mapOf(testDescriptor to TestFactory::class.java)
        )

        val cInstance: TestComponentInstance = bInstance.new(
            testDescriptor,
            TestConfiguration()
        )

        runBootBlocking {
            cInstance.start()
        }

        cInstance.end()
    }

    @Test
    fun `Test ignores boot loaded dependencies correctly`() {
        val descriptor = SimpleMavenDescriptor.parseDescription("dev.extframework:archives:1.1-SNAPSHOT")!!

        val instance: BootInstance = testBootInstance(
            mapOf(),
            dependencies = setOf(descriptor)
        )

        runBootBlocking {
            val resolver = instance.dependencyTypes.get("simple-maven")!!.resolver as MavenDependencyResolver

            instance.archiveGraph.cache(
                SimpleMavenArtifactRequest(descriptor),
                SimpleMavenRepositorySettings.default(url = "http://maven.extframework.dev/snapshots"),
                resolver
            )().merge()

            val node = instance.archiveGraph.get(
                descriptor,
                resolver
            )().merge()

            check(node.archive == null)
            check(node.parents.isEmpty())
        }
    }

    @Test
    fun `Test Archive graph ignores root versions correctly`() {
        val descriptor = SimpleMavenDescriptor.parseDescription("dev.extframework:archives:1.1-SNAPSHOT")!!

        val instance: BootInstance = testBootInstance(
            mapOf(),
            dependencies = setOf(descriptor)
        )

        check(instance.archiveGraph[SimpleMavenDescriptor.parseDescription("dev.extframework:archives:asdf-SNAPSHOT")!!] != null) {"Version component wasnt ignored!"}
    }
}