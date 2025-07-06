package com.kaolinmc.`object`

public interface ObjectContainer<T: ObjectContainer.IDed> : Map<String, T> {
    public fun register(obj: T): Boolean

    public interface IDed {
        public val id: String
    }
}