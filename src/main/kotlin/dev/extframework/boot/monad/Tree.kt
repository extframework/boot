package dev.extframework.boot.monad

/**
 * A monad representing a tree of any type T.
 *
 * @param T the type of the value contained within the tree.
 * @property item the value contained within the tree node.
 * @property parents the list of parent nodes of the current tree node.
 */
public class Tree<out T>(
    item: T,
    parents: List<Tree<T>>
) : AndMany<T, Tree<T>>(item, parents) {
    override fun toString(): String {
        return "Tree -> $item"
    }
}

/**
 * Applies a transform function to each value in the tree and returns a new tree with the transformed values.
 *
 * @param transform the function to transform each value in the tree.
 *
 * @param T the type of the value contained within the tree.
 * @param V the type of the value returned by the transform function.
 *
 * @return a new tree with the transformed values.
 */
public fun <T, V> Tree<T>.map(
    transform: (T) -> V,
): Tree<V> {
    return Tree(transform(item), parents.map { it.map(transform) })
}

public fun <T, V> Tree<T>.mapWithTree(
    transform: (Tree<T>) -> V,
): Tree<V> {
    return Tree(transform(this), parents.map { it.mapWithTree(transform) })
}

/**
 * Applies the given function [onEach] to each element in the tree, including the root node and all its children.
 *
 * @param onEach the function to apply to each element in the tree.
 *               The function takes a single argument of type [T] and does not return a value.
 *
 * @param T the type of the value contained within the tree.
 */
public fun <T> Tree<T>.forEach(
    onEach: (T) -> Unit
) {
    onEach(this.item)

    parents.forEach {
        it.forEach(onEach)
    }
}

/**
 * Performs a breadth-first traversal of the tree and applies the given [block] function to each node.
 *
 * @param T the type of the value contained within the tree nodes.
 *
 * @param block the function to be applied to each node.
 */
public fun <T> Tree<T>.forEachBfs(
    block: (T) -> Unit
) {
    val queue: MutableList<Tree<T>> = ArrayList()

    queue.add(this)

    while (queue.isNotEmpty()) {
        val current = queue.first()
        queue.remove(current)

        block(current.item)

        queue.addAll(current.parents)
    }
}

/**
 * Searches for the first element in the tree that satisfies the given condition. Applies
 * a depth first search.
 *
 * @param condition The condition that the element should satisfy.
 * @param T The type of the value contained within the tree.
 *
 * @return The first element that satisfies the condition, or null if no such element is found.
 */
public fun <T> Tree<T>.find(
    condition: (T) -> Boolean
): T? = if (condition(item)) item
else parents.firstNotNullOfOrNull {
    it.find(condition)
}

/**
 * Finds a branch in a tree that satisfies the given condition.
 *
 * @param condition the condition to satisfy.
 *
 * @return the tree branch that satisfies the condition, or null if no branch satisfies the condition.
 */
public fun <T> Tree<T>.findBranch(
    condition: (T) -> Boolean
): Tree<T>? = if (condition(item)) this
else parents.firstNotNullOfOrNull {
    it.findBranch(condition)
}

/**
 * Replaces the current tree with a new tree by applying the given [doReplace] function.
 * Then traverses the tree applying this replace at each branch point.
 *
 * @param doReplace the function that takes the current tree and returns the new tree.
 *
 * @return the new tree.
 */
public fun <T> Tree<T>.replace(
    doReplace: (Tree<T>) -> Tree<T>
): Tree<T> {
    val thisTree = doReplace(this)

    return Tree(
        item = thisTree.item,
        parents = parents.map { it.replace(doReplace) }
    )
}

/**
 * Converts an instance of [AndMany] to a [Tree] monad.
 *
 * @param T the type of the item in the [Tree].
 *
 * @receiver the [AndMany] instance to be converted.
 *
 * @return a new [Tree] instance with the same item and parents as the [AndMany] instance.
 */
public fun <T> AndMany<T, Tree<T>>.toTree() : Tree<T> = Tree(
    item, parents
)

/**
 * Converts the given tree to a list in a depth-first searched
 * order.
 *
 * @return The list that was created
 */
public fun <T> Tree<T>.toList(): List<T> {
    val list = mutableListOf<T>()
    forEach { list.add(it) }

    return list
}

private data class Current<T>(
    var index: Int,
    val tree: Tree<T>
)
public fun <T> Tree<T>.iterator() : Iterator<T> = object: Iterator<T> {
    // Used to keep track of where we are in the tree after each
    val stack: MutableList<Current<T>> = mutableListOf(
        Current(-1, this@iterator)
    )

    override fun hasNext(): Boolean =
        !stack.all { it.index == it.tree.parents.size }

    override fun next(): T {
        val current = stack.last()

        val item = if (current.index == -1) {
            current.tree.item
        } else if (current.tree.parents.size == current.index) {
            stack.removeLast()
            next()
        } else {
            stack.add(
                Current(
                    -1,
                    current.tree.parents[current.index],
                )
            )
            next()
        }

        current.index++

        return item
    }
}

public fun <T> Tree<T>.asSequence(): Sequence<T>  = Sequence {
    iterator()
}