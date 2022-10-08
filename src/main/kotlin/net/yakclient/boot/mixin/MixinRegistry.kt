package net.yakclient.boot.mixin

import net.yakclient.archives.transform.TransformerConfig

//public class MixinRegistry(
//    private val injectors: Map<String, MetadataInjector<*>>
//) {
//    private val _injections: MutableMap<String, MutableList<InjectionReference>> = HashMap()
//    public val injections: Map<String, List<InjectionReference>>
//        get() = _injections.mapValues { it.value.toList() }.toMap()
//
//    public fun register(to: String, injection: InjectionMetadata): InjectionReference {
//        if (!_injections.containsKey(to)) _injections[to] = ArrayList()
//        val reference = InjectionReference(to, injection)
//
//        _injections[to] = _injections[to]!!.apply { add(reference) }
//
//        return reference
//    }
//
//    public fun transformerFor(to: String): TransformerConfig = _injections[to]
//        ?.map(InjectionReference::metadata)
//        ?.sortedWith { a, b -> a.priority - b.priority }
//        ?.map {
//            it to (injectors[it::class.java.name]
//                ?: throw IllegalArgumentException("Unknown injection metadata type: '$it'. No matching injector found!"))
//        }
//        ?.map { (it.second as MetadataInjector<InjectionMetadata>).inject(it.first) }
//        ?.fold(TransformerConfig.MutableTransformerConfiguration()) { acc, it ->
//            acc.transformClass(it.ct)
//            acc.transformMethod(it.mt)
//            acc.transformField(it.ft)
//
//            acc
//        } ?: TransformerConfig.MutableTransformerConfiguration()
//
//    public data class InjectionReference internal constructor(
//        val to: String,
//        val metadata: InjectionMetadata
//    )
//}
