package net.yakclient.boot.component

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.yakclient.boot.BootInstance
import net.yakclient.boot.component.context.ContextNodeTree
import net.yakclient.boot.component.context.ContextNodeValue

public abstract class ComponentFactory<T: ComponentConfiguration, out I: ComponentInstance<T>>(
        protected val boot: BootInstance
) {
    public abstract fun new(configuration: T) : I

    public abstract fun parseConfiguration(value: ContextNodeValue) : T
}