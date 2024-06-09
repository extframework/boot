package dev.extframework.boot.component.context.impl

import dev.extframework.boot.component.context.ContextNodeTree
import dev.extframework.boot.component.context.ContextNodeValue

internal class ContextNodeTreeImpl(
        private val raw: Map<String, Any>
) : ContextNodeTree {
    override fun get(name: String): ContextNodeValue? {
        return raw[name]?.let(::ContextNodeValueImpl)
    }

    override fun keys(): Set<String> {
        return raw.keys
    }

}