package dev.extframework.`object`

public open class ObjectContainerImpl<T: ObjectContainer.IDed> @JvmOverloads constructor(
    private val delegate: MutableMap<String, T> = HashMap()
) : ObjectContainer<T>, Map<String, T> by delegate {
    override fun register(obj: T): Boolean {
        val contained = contains(obj.id)
        delegate[obj.id] = obj
        return !contained
    }
}