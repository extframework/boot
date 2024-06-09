package dev.extframework.boot.component

import dev.extframework.boot.BootInstance
import dev.extframework.boot.component.context.ContextNodeValue

public abstract class ComponentFactory<T: ComponentConfiguration, out I: ComponentInstance<T>>(
        protected val boot: BootInstance
) {
    public abstract fun new(configuration: T) : I

    public abstract fun parseConfiguration(value: ContextNodeValue) : T
}