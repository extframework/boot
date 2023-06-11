import net.yakclient.boot.BootInstance
import net.yakclient.boot.component.ComponentConfiguration
import net.yakclient.boot.component.ComponentFactory
import net.yakclient.boot.component.ComponentInstance
import net.yakclient.boot.component.artifact.SoftwareComponentDescriptor
import net.yakclient.boot.component.context.ContextNodeValue
import net.yakclient.boot.new
import net.yakclient.boot.test.testBootInstance
import kotlin.test.Test

class BootTestTest {
    @Test
    fun `Test boot instance setup`() {
        class TestConfiguration: ComponentConfiguration
        class TestComponentInstance: ComponentInstance<TestConfiguration> {
            override fun start() {
                println("Started!")
            }

            override fun end() {
                println("Ended :)")
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
                mapOf(testDescriptor to  TestFactory::class.java)
        )

        val cInstance: TestComponentInstance =  bInstance.new(
                testDescriptor,
                TestConfiguration()
        )

        cInstance.start()

        cInstance.end()
    }
}