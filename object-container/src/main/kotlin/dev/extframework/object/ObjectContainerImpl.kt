package dev.extframework.`object`

public open class ObjectContainerImpl<T> : MutableObjectContainer<T> {
    private val delegate: MutableMap<String, T> = HashMap()

    override fun get(name: String): T? = delegate[name]

    override fun has(name: String): Boolean = delegate.containsKey(name)

    override fun objects(): Map<String, T> {
        return delegate.toMap()
    }

    override fun register(name: String, obj: T): Boolean {
        if (has(name)) return false
        delegate[name] = obj
        return true
    }
}