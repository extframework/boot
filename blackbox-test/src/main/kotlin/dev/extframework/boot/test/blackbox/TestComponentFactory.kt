package dev.extframework.boot.test.blackbox

import dev.extframework.boot.BootInstance
import dev.extframework.boot.component.ComponentFactory
import dev.extframework.boot.component.context.ContextNodeValue

public class TestComponentFactory(
    boot: BootInstance
) : ComponentFactory<TestComponentConfiguration, TestComponent>(boot) {
    override fun new(configuration: TestComponentConfiguration): TestComponent {
        return TestComponent()
    }

    override fun parseConfiguration(value: ContextNodeValue): TestComponentConfiguration {
        return TestComponentConfiguration()
    }
}