package dev.extframework.boot.monad

/**
 * A monad representing any type A tagged with type B.
 *
 * @param A the type of the value.
 * @param B the type of the tag.
 *
 * @property value the value of type A.
 * @property tag the tag of type B.
 */
public data class Tagged<out A,  out B>(
    val value: A,
    val tag: B
)

/**
 * Creates a new instance of `Tagged` representing type `A` tagged with type `B`.
 *
 * @see Tagged
 *
 * @param A the type of the value.
 * @param B the type of the tag.
 *
 * @param tag the tag of type `B` to attach to the value.
 * @return a new instance of `Tagged` representing the tag attachment.
 */
public fun <A, B> A.tag(tag: B) : Tagged<A, B> = Tagged(this, tag)

/**
 * Creates a new tree where each value is tagged with type `B`.
 *
 * @param tagger a function that takes a value of type `A` and returns a tag of type `B` for that value.
 *
 * @param A the type of the original value in the tree.
 * @param B the type of the tag to attach to each value.
 *
 * @return a new tree where each value is of type `Tagged<A, B>`.
 */
public inline fun <A, B> Tree<A>.tag(
    crossinline tagger: (A) -> B
) : Tree<Tagged<A, B>> = map {
    it.tag(tagger(it))
}

/**
 * Returns a new instance of `Tree<Tagged<A, B>>` by applying `tagger` function on each item in the original `Tree<A>`.
 *
 * @see Tree
 * @see Tagged
 * @see tag
 * @see mapWithTree
 *
 * @param A the type of the original value.
 * @param B the type of the tag.
 *
 * @param tagger the function that tags each item with type B in the original `Tree<A>`.
 *
 * @return a new `Tree<Tagged<A, B>>` where each item is tagged with the result of `tagger` function.
 */
public inline fun <A, B> Tree<A>.tagWithTree(
    crossinline tagger: (Tree<A>) -> B
) : Tree<Tagged<A, B>> = mapWithTree {
    it.item.tag(tagger(it))
}