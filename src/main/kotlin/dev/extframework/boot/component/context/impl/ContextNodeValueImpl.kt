package dev.extframework.boot.component.context.impl

import dev.extframework.boot.component.context.ContextNodeType
import dev.extframework.boot.component.context.ContextNodeValue

internal class ContextNodeValueImpl(
        private val raw: Any
) : ContextNodeValue {
    override fun <T> tryType(type: ContextNodeType<T>): T? {
        return type.accept(raw)
    }
}