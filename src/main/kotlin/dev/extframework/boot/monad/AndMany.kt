package dev.extframework.boot.monad

/**
 * The [AndMany] class represents a monad of type [A] and a list of parent items of type [B].
 *
 * @param A the type of the item.
 * @param B the type of the parent items.
 *
 * @property item the item.
 * @property parents the list of parent items.
 */
public open class AndMany<out A, out B>(
    public val item: A,
    public val parents: List<B>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AndMany<*, *>) return false

        if (item != other.item) return false
        if (parents != other.parents) return false

        return true
    }

    override fun hashCode(): Int {
        var result = item?.hashCode() ?: 0
        result = 31 * result + parents.hashCode()
        return result
    }

    public operator fun component1(): A = item

    public operator fun component2(): List<B> = parents
}

/**
 * Relates type A to its parents of type B in the monad [AndMany]
 *
 * @param A the type of the left-hand side item.
 * @param B the type of the right-hand side list items.
 *
 * @param parents the list of right-hand side items.
 *
 * @return an instance of `AndMany<A, B>` representing a monad with the left-hand side item and the list of right-hand side items.
 */
public infix fun <A, B> A.andMany(parents: List<B>): AndMany<A, B> = AndMany(this, parents)

/**
 * Applies a transformation to the item of type [A] in the [AndMany] monad and returns a new [AndMany] monad with the transformed item.
 *
 * @param A the type of the original item.
 * @param V the type of the transformed item.
 * @param B the type of the parent items.
 *
 * @param map the transformation function to be applied to the item.
 *
 *  @return a new [AndMany] monad with the transformed item.
 */
public fun <A, V, B> AndMany<A, B>.mapItem(
    map: (A) -> V
) : AndMany<V, B> = AndMany(map(item), parents)

/**
 * Maps the parent items in the [AndMany] monad to a new type using the provided [map] function.
 *
 * @param A the type of the item.
 * @param V the type of the parent items after mapping.
 * @param B the type of the parent items before mapping.
 *
 * @param map the function to map parent items of type [B] to type [V].
 *
 * @return a new AndMany monad with parent items of type [V].
 */
public fun <A, V, B> AndMany<A, B>.mapParents(
    map: (B) -> V
) : AndMany<A, V> = AndMany(item, parents.map(map))