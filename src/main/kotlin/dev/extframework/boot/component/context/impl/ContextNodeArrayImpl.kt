package dev.extframework.boot.component.context.impl

import dev.extframework.boot.component.context.ContextNodeArray
import dev.extframework.boot.component.context.ContextNodeValue

internal class ContextNodeArrayImpl(
        private val raw: List<Any>
) : ContextNodeArray {
    override val size: Int = raw.size

    override fun get(i: Int): ContextNodeValue? {
        return raw.getOrNull(i)?.let(::ContextNodeValueImpl)
    }

    override fun list(): List<ContextNodeValue> {
        return raw.map(::ContextNodeValueImpl)
    }
}