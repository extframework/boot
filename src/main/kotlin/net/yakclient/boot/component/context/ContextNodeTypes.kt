package net.yakclient.boot.component.context

import net.yakclient.boot.component.context.impl.ContextNodeArrayImpl
import net.yakclient.boot.component.context.impl.ContextNodeTreeImpl
import net.yakclient.boot.component.context.impl.ContextNodeValueImpl

public object ContextNodeTypes {
    public fun newValueType(any: Any) : ContextNodeValue = ContextNodeValueImpl(any)

    public val Tree: ContextNodeType<ContextNodeTree> = ContextNodeType {
        val map = (it as? Map<String, Any>) ?: return@ContextNodeType null

        ContextNodeTreeImpl(map)
    }

    public val Array : ContextNodeType<ContextNodeArray> = ContextNodeType {
        val map = (it as? List<Any>) ?: return@ContextNodeType null

        ContextNodeArrayImpl(map)
    }

    public val String: ContextNodeType<String> = ContextNodeType { it.toString() }

    public val StrictString: ContextNodeType<String> = ContextNodeType { it as? String }

    public val Int: ContextNodeType<Int> = ContextNodeType { it as? Int }

    public val Boolean: ContextNodeType<Boolean> = ContextNodeType { it as? Boolean }

    public val Double: ContextNodeType<Double> = ContextNodeType { it as? Double }

    public val Float: ContextNodeType<Float> = ContextNodeType { it as? Float }

    public val Long: ContextNodeType<Long> = ContextNodeType { it as? Long }

    public val Number: ContextNodeType<Number> = ContextNodeType { it as? Number }

    public val Short: ContextNodeType<Short> = ContextNodeType { it as? Short }

    public val Byte: ContextNodeType<Byte> = ContextNodeType { it as? Byte }

    public val Nothing: ContextNodeType<Unit> = ContextNodeType { it as? Unit }
}

