package net.yakclient.boot.component.context.impl

import net.yakclient.boot.component.context.ContextNodeTree
import net.yakclient.boot.component.context.ContextNodeValue

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