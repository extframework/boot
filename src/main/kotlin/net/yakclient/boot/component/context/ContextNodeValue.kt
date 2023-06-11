package net.yakclient.boot.component.context

public interface ContextNodeValue {
    public fun <T> tryType(type: ContextNodeType<T>) : T?

    public fun <T> coerceType(type: ContextNodeType<T>) : T {
        val value = tryType(type)
        checkNotNull(value) {"Value failed to coerce into type: '$type'"}
        return value
    }

    public fun tryTree() : ContextNodeTree? = tryType(ContextNodeTypes.Tree)

    public fun tryArray() : ContextNodeArray? = tryType(ContextNodeTypes.Array)

    public fun coerceTree(): ContextNodeTree {
        val tryTree = tryTree()
        checkNotNull(tryTree) {"Value failed to coerce into tree type"}
        return tryTree
    }

    public fun coerceArray() : ContextNodeArray {
        val tryArray = tryArray()
        checkNotNull(tryArray) {"Value failed to coerce into array type"}
        return tryArray
    }
}