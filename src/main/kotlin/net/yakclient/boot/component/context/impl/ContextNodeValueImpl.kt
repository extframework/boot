package net.yakclient.boot.component.context.impl

import net.yakclient.boot.component.context.ContextNodeType
import net.yakclient.boot.component.context.ContextNodeValue

internal class ContextNodeValueImpl(
        private val raw: Any
) : ContextNodeValue {
    override fun <T> tryType(type: ContextNodeType<T>): T? {
        return type.accept(raw)
    }
}