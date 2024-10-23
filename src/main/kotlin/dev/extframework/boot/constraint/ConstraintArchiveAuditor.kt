package dev.extframework.boot.constraint

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import dev.extframework.boot.archive.*
import dev.extframework.boot.monad.*
import dev.extframework.common.util.LazyMap
import dev.extframework.common.util.filterDuplicates

public class ConstraintArchiveAuditor(
    private val negotiators: List<ConstraintNegotiator<*>>,
) : ArchiveTreeAuditor {
    private fun doConstraints(
        tree: Tree<IArchive<*>>,
        constraintPrototypes: List<Constrained<*>>,

        trace: ArchiveTrace
    ): Job<Tree<IArchive<*>>> = job {
        val cachedConstraints = LazyMap { d: ArtifactMetadata.Descriptor ->
            (negotiators.find {
                it.descriptorType.isInstance(d)
            } as? ConstraintNegotiator<ArtifactMetadata.Descriptor>)?.classify(d) ?: Any()
        }

        fun classify(descriptor: ArtifactMetadata.Descriptor): Any = cachedConstraints[descriptor]!!

        val list = tree.toList()
        val uniqueConstraints = list
            .map { classify(it.descriptor) }
            .filterDuplicates()
            .size

        fun Tree<IArchive<*>>.findParents(
            any: Any,
            parent: Any? = null
        ): List<Any> {
            val thisClassifier = classify(item.descriptor)
            return (if (thisClassifier == any) {
                listOfNotNull(parent)
            } else emptyList()) + parents.flatMap {
                it.findParents(any, thisClassifier)
            }
        }

        val constrained = HashSet<Any>()

        fun Tree<IArchive<*>>.constrain(
            classifier: Any,
        ): Tree<IArchive<*>> {
            if (!constrained.add(classifier)) return this

            val parents = findParents(classifier, null)

            val newTree = parents.fold(this) { acc, it ->
                acc.constrain(it)
            }

            val group = newTree
                .asSequence()
                .filter { classify(it.descriptor) == classifier }
                .map {
                    Constrained(
                        it.descriptor,
                        if (it is ClassLoadedArchiveNode<*> && it.handle != null) ConstraintType.BOUND
                        else ConstraintType.NEGOTIABLE
                    )
                }
                .toSet()

            val negotiator = group
                .firstOrNull()
                ?.descriptor
                ?.let { d ->
                    negotiators.find { it.descriptorType.isInstance(d) }
                } as ConstraintNegotiator<ArtifactMetadata.Descriptor>? ?: return newTree

            val negotiated = negotiator.negotiate(
                group + constraintPrototypes.filter {
                    negotiator.descriptorType.isInstance(it.descriptor) && negotiator.classify(it.descriptor) == classifier
                } as List<Constrained<ArtifactMetadata.Descriptor>>,
                trace
            )().merge()

            val replaceWith = tree.findBranch {
                it.descriptor == negotiated
            } ?: throw ConstraintException.ConstraintNotFound(trace, negotiated, newTree)

            val replaceWithClassifier = classify(negotiated)
            return newTree.replace {
                if (classify(it.item.descriptor) == replaceWithClassifier)
                    replaceWith
                else it
            }
        }

        list.fold(tree) { acc, it ->
            if (constrained.size == uniqueConstraints) return@fold acc

            acc.constrain(
                classify(it.descriptor),
            )
        }
    }

    override fun audit(event: ArchiveTreeAuditContext): Job<ArchiveTreeAuditContext> = job {
        val tree = event.tree

        val resolvers = tree
            .asSequence()
            .associate { it.value.descriptor to it.tag }

        event.copy(
            doConstraints(
                tree.map { it.value },
                if (negotiators.any {
                        it.descriptorType.isInstance(tree.item.value.descriptor)
                    }) listOf(Constrained(tree.item.value.descriptor, ConstraintType.BOUND)) else listOf(),
                event.trace
            )().merge().tag {
                resolvers[it.descriptor]!!
            }
        )
    }
}