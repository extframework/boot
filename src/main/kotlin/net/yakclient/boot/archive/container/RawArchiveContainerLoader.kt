package net.yakclient.boot.archive.container

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