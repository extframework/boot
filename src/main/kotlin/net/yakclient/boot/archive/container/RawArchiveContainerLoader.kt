package net.yakclient.boot.archive.container

import com.durganmcbroom.jobs.JobResult
import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.ResolutionResult
import net.yakclient.boot.archive.ArchiveAccessTree
import net.yakclient.boot.archive.ArchiveException
import net.yakclient.boot.archive.ArchiveRelationship
import net.yakclient.boot.archive.BasicArchiveResolutionProvider
import net.yakclient.boot.container.ContainerArchiveLoader
import net.yakclient.boot.container.ArchiveContainerInfo
import net.yakclient.boot.loader.ArchiveClassProvider
import net.yakclient.boot.loader.ArchiveSourceProvider
import net.yakclient.boot.loader.DelegatingClassProvider
import net.yakclient.boot.loader.IntegratedLoader
import java.nio.file.Path

//public data class RawArchiveContainerInfo(
//    public val name: String,
//    public val path: Path,
//    override val access: ArchiveAccessTree,
//) : ArchiveContainerInfo
//
//public class RawArchiveContainerLoader(
//    private val resolver: BasicArchiveResolutionProvider<*, *>,
//    private val parent: ClassLoader,
//) : ContainerArchiveLoader<RawArchiveContainerInfo> {
//    override suspend fun load(info: RawArchiveContainerInfo): JobResult<ArchiveHandle, ArchiveException> {
//        return resolver.resolve(
//            info.path,
//            { ref ->
//                IntegratedLoader(
//                    name = info.name,
//                    sourceProvider = ArchiveSourceProvider(ref),
//                    classProvider = DelegatingClassProvider(
//                        info.access.targets.mapNotNull {
//                            it.target.archive
//                        }.map { ArchiveClassProvider(it) }
//                    ),
//                    parent = parent
//                )
//            },
//            info.access.targets.filter { it.relationship == ArchiveRelationship.DIRECT }.mapNotNullTo(HashSet()) {
//                it.target.archive
//            }
//        ).map(ResolutionResult::archive)
//    }
//}