package net.yakclient.boot.plugin.store

//public class PluginDataAccess(
//    private val path: Path
//) : DataAccess<PluginKey, PluginData> {
//    private val mapper = ObjectMapper().registerModule(KotlinModule())
//
//    override fun read(key: PluginKey): PluginData? {
//        val desc = key.desc
//        val basePath =
//            path resolve desc.group.replace('.', File.separatorChar) resolve desc.artifact resolve desc.version
//
//        val prmPath = basePath resolve "${desc.artifact}-${desc.version}.prm.json"
//
//        if (!prmPath.exists()) return null
//
//        val runtimeModel = mapper.readValue<PluginRuntimeModel>(prmPath.toFile())
//        val packaging = runtimeModel.packaging
//
//        val jarPath = basePath resolve "${desc.artifact}-${desc.version}.${packaging}"
//        val resource = if (jarPath.exists()) LocalResource(jarPath.toUri()) else null
//
//        return PluginData(
//            key,
//            resource,
//            runtimeModel.plugins.map(PluginDependency::toKey),
//            runtimeModel.dependencies.map(PluginDependency::toDescriptor),
//            runtimeModel,
//        )
//    }
//
//    override fun write(key: PluginKey, value: PluginData) {
//        val desc = key.desc
//        val basePath =
//            path resolve desc.group.replace('.', File.separatorChar) resolve desc.artifact resolve desc.version
//
//        val prmPath = basePath resolve "${desc.artifact}-${desc.version}.prm.json"
//
//        prmPath.writeBytes(mapper.writeValueAsBytes(value.runtimeModel))
//
//        if (value.archive != null) {
//            val jarPath = basePath resolve "${desc.artifact}-${desc.version}.${value.runtimeModel.packaging}"
//
//            Channels.newChannel(value.archive.open()).use { cin ->
//                jarPath.make()
//                FileOutputStream(jarPath.toFile()).use { fout ->
//                    fout.channel.transferFrom(cin, 0, Long.MAX_VALUE)
//                }
//            }
//        }
//    }
//}