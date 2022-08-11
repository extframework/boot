package net.yakclient.boot.container.volume

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import net.yakclient.common.util.LazyMap
import net.yakclient.common.util.make
import net.yakclient.common.util.resolve
//
//public object VolumeStore {
//    private const val VOLUME_META_NAME = "volumes-store.json"
//
//    private val volumeStorePath = YakClient.settings.volumeStorePath
//    private val volumeMetaPath = volumeStorePath resolve VOLUME_META_NAME
//    private val volumeInfo: MutableMap<String, VolumeInfo>
//    private val volumes: Map<String, ContainerVolume>
//
//    private val mapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule())
//
//    init {
//        val metaFile = volumeMetaPath.toFile()
//
//        if (volumeMetaPath.make()) metaFile.writeText(mapper.writeValueAsString(setOf<VolumeInfo>()))
//
//        volumeInfo = mapper.readValue<Set<VolumeInfo>>(metaFile).associateByTo(HashMap()) { it.name }
//
//        volumes = LazyMap { name ->
//            val path = volumeInfo[name]?.path ?: run {
//                volumeInfo[name] = VolumeInfo(name, name)
//
//                volumeMetaPath.make()
//                metaFile.writeText(mapper.writeValueAsString(volumeInfo.values))
//                (volumeStorePath resolve name).toFile().mkdirs()
//
//                name
//            }
//
//            RootVolume.derive(name, volumeStorePath resolve path)
//        }
//    }
//
//    public operator fun get(name: String): ContainerVolume = volumes[name]!!
//
//    public fun contains(name: String) : Boolean = volumeInfo.contains(name)
//
//    private data class VolumeInfo(
//        val name: String,
//        val path: String
//    )
//}