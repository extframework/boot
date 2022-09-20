package net.yakclient.boot.plugin

//import net.yakclient.boot.plugin.artifact.PluginDescriptor
//
//public data class PluginKey(
//    val desc: PluginDescriptor
//) {
//    override fun equals(other: Any?): Boolean {
//        if (this === other) return true
//        if (other !is PluginKey) return false
//
//        if (desc.group != other.desc.group) return false
//        if (desc.artifact != other.desc.artifact) return false
//
//        return true
//    }
//
//    override fun hashCode(): Int {
//        var result = desc.group.hashCode()
//        result = 31 * result + desc.artifact.hashCode()
//        return result
//    }
//}
