package dev.extframework.boot.component.context

public interface ContextNodeArray {
    public val size: Int

    public operator fun get(i: Int) : ContextNodeValue?

    public fun list() : List<ContextNodeValue>
}