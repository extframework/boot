package net.yakclient.boot.component

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.jobs.JobResult
import com.durganmcbroom.jobs.jobScope
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.zip.classLoaderToArchive
import net.yakclient.boot.BootInstance
import net.yakclient.boot.archive.*
import net.yakclient.boot.component.artifact.*
import net.yakclient.boot.dependency.DependencyNode
import net.yakclient.boot.dependency.DependencyResolverProvider
import net.yakclient.boot.dependency.DependencyTypeContainer
import net.yakclient.boot.dependency.cacheArtifact
import net.yakclient.boot.maven.MavenLikeResolver
import net.yakclient.boot.util.firstNotFailureOf
import net.yakclient.boot.util.toSafeResource
import net.yakclient.common.util.resource.ProvidedResource
import java.lang.reflect.Constructor
import java.net.URI
import kotlin.reflect.KClass

public open class SoftwareComponentResolver(
    private val resolutionProvider: ArchiveResolutionProvider<*>,
    private val dependencyProviders: DependencyTypeContainer,
    private val bootInstance: BootInstance
) : MavenLikeResolver<
        SoftwareComponentArtifactRequest,
        SoftwareComponentNode,
        SoftwareComponentRepositorySettings,
        SoftwareComponentRepositoryStub,
        SoftwareComponentArtifactMetadata> {
    override val name: String = "component"
    override val nodeType: KClass<SoftwareComponentNode> = SoftwareComponentNode::class
    override val metadataType: KClass<SoftwareComponentArtifactMetadata> = SoftwareComponentArtifactMetadata::class
    override val factory: RepositoryFactory<SoftwareComponentRepositorySettings, SoftwareComponentArtifactRequest, *, ArtifactReference<SoftwareComponentArtifactMetadata, *>, *> =
        SoftwareComponentRepositoryFactory

    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    override suspend fun load(
        data: ArchiveData<SoftwareComponentDescriptor, CachedArchiveResource>,
        resolver: ChildResolver
    ): JobResult<SoftwareComponentNode, ArchiveException> = jobScope {
        val (childData, dependencyData) = data.children.partition { it.resolver == this@SoftwareComponentResolver.name }

        val asyncChildren = childData.map {
            async {
                resolver.load(
                    it.descriptor as SoftwareComponentDescriptor, this@SoftwareComponentResolver
                )
            }
        }
        val asyncDependencies = dependencyData.map {
            val localResolver = (dependencyProviders.get(it.resolver)?.resolver
                ?: fail(ArchiveException.ArchiveTypeNotFound(it.resolver, trace()))) as ArchiveNodeResolver<ArtifactMetadata.Descriptor, *, DependencyNode, *, *, *>

            async {
                resolver.load(
                    it.descriptor, localResolver
                )
            }
        }

        val children = asyncChildren.awaitAll()
        val dependencies = asyncDependencies.awaitAll()

        val result = data.resources["jar.jar"]?.let {
            val parents =
                children.mapNotNullTo(HashSet(), SoftwareComponentNode::archive) + dependencies.mapNotNullTo(
                    HashSet(),
                    DependencyNode::archive
                ) + classLoaderToArchive(this::class.java.classLoader)

            resolutionProvider.resolve(
                it.path,
                { a -> SofwareComponentClassLoader(this::class.java.classLoader, a, parents) },
                parents
            )
        }?.attempt()

        val runtimeModel =
            mapper.readValue<SoftwareComponentModel>(
                (data.resources["model.json"]
                    ?: fail(ArchiveException.IllegalState("Failed to find model.json in resources", trace()))).path.toFile()
            )

        val archive = result?.archive

        SoftwareComponentNode(
            data.descriptor,
            archive,
            children.toSet(),
            dependencies.toSet(),
            runtimeModel,
            archive?.let { loadFactory(it, runtimeModel) },
        )
    }

    private fun <T : Any> Class<T>.tryGetConstructor(vararg params: Class<*>): Constructor<T>? =
        net.yakclient.common.util.runCatching(NoSuchMethodException::class) { this.getConstructor(*params) }

    private fun loadFactory(archive: ArchiveHandle, runtimeModel: SoftwareComponentModel): ComponentFactory<*, *> {
        val loadClass = archive.classloader.loadClass(runtimeModel.entrypoint)
        return (loadClass.tryGetConstructor(BootInstance::class.java)?.newInstance(bootInstance)
            ?: loadClass.tryGetConstructor()?.newInstance()) as ComponentFactory<*, *>
    }

    override suspend fun cache(
        ref: ArtifactReference<SoftwareComponentArtifactMetadata, ArtifactStub<SoftwareComponentArtifactRequest, SoftwareComponentRepositoryStub>>,
        helper: ArchiveCacheHelper<SoftwareComponentRepositoryStub, SoftwareComponentRepositorySettings>
    ): JobResult<ArchiveData<SoftwareComponentDescriptor, CacheableArchiveResource>, ArchiveException> = jobScope {
        val componentChildren: List<ArchiveChild<SimpleMavenDescriptor>> = ref.children.map { stub ->
            stub.candidates.firstNotFailureOf {
                helper.cache(stub.request, helper.resolve(it).casuallyAttempt(), this@SoftwareComponentResolver)
            }.attempt()
        }

        val dependencyChildren: List<ArchiveChild<*>> = ref.metadata.dependencies.map {
            val provider: DependencyResolverProvider<*, *, *> =
                dependencyProviders.get(it.type) ?: fail(ArchiveException.ArchiveTypeNotFound(it.type, trace()))

            provider.cacheArtifact(
                it.repositorySettings,
                it.request,
                trace(),
                helper,
            ).attempt()
        }

        ArchiveData(
            ref.metadata.descriptor,
            mapOf(
                "model.json" to CacheableArchiveResource(
                    ProvidedResource(URI.create(ref.metadata.resource!!.location)) {
                        mapper.writeValueAsBytes(ref.metadata.runtimeModel)
                    }
                ),
                "jar.jar" to CacheableArchiveResource(
                    ref.metadata.resource!!.toSafeResource()
                )
            ),
            componentChildren + dependencyChildren
        )
    }

//    override fun cacherOf(settings: SoftwareComponentRepositorySettings): SoftwareComponentCacher =
//        SoftwareComponentCacher(
//            SoftwareComponentRepositoryFactory.createContext(settings)
//        )

//    override fun isCached(descriptor: SoftwareComponentDescriptor): Boolean {
//        return store.contains(descriptor)
//    }

//    private suspend fun <
//            S : RepositorySettings,
//            K : ArtifactMetadata.Descriptor,
//            R : ArtifactRequest<K>> DependencyResolverProvider<K, R, S>.getArtifact(
//        pRequest: Map<String, String>,
//    ): JobResult<DependencyNode, ArchiveLoadException> { // Dont need extra context here
//        val request: R = parseRequest(pRequest)
//            ?: return JobResult.Failure(ArchiveLoadException.DependencyInfoParseFailed("Failed to parse artifact request: '$pRequest'"))
//
//        return resolver.load(request.descriptor)
//    }


    //    private suspend fun load(
//        data: SoftwareComponentData
//    ): JobResult<SoftwareComponentNode, ArchiveLoadException> =
//        job(JobName("Load software component: '${data.key}'")) {
////            graph[data.key] ?: run {
//            val children: List<SoftwareComponentNode> = data.children
//                .map { store.get(it) to it }
//                .map {
//                    it.first
//                        ?: throw java.lang.IllegalStateException("Dependency: '${it.second}', child of '${data.key}' could not be found in the cache. This means your software component cache has been invalidated and you should delete the directory.")
//                }.map {
//                    async {
//                        withWeight(1) {
//                            load(it)
//                        }
//                    }
//                }.map {
//                    it.await().attempt()
//                }
//
//            val dependencies = data.dependencies.map {
//                async {
//                    withWeight(1) {
//                        dependencyProviders.get(it.type)?.getArtifact(it.request) ?: fail(
//                            ArchiveLoadException.DependencyTypeNotFound(it.type)
//                        )
//                    }
//                }
//            }.map { it.await().attempt() }
//
//            val result = data.archive?.let {
//                val parents =
//                    children.mapNotNullTo(HashSet(), SoftwareComponentNode::archive) + dependencies.mapNotNullTo(
//                        HashSet(),
//                        DependencyNode::archive
//                    ) + classLoaderToArchive(this::class.java.classLoader)
//
//                resolutionProvider.resolve(
//                    it,
//                    { a -> SofwareComponentClassLoader(this::class.java.classLoader, a, parents) },
//                    parents
//                )
//            }?.attempt()
//
//            val archive = result?.archive
//
//            SoftwareComponentNode(
//                data.key,
//                archive,
//                children.toSet(),
//                dependencies.toSet(),
//                data.runtimeModel,
//                archive?.let { loadFactory(it, data.runtimeModel) },
//            )
////            }
//        }
//


    //    public inner class SoftwareComponentCacher(
//        override val resolver: ResolutionContext<SoftwareComponentArtifactRequest, SoftwareComponentArtifactStub, ArtifactReference<*, SoftwareComponentArtifactStub>>,
//    ) : ArchiveCacher<SoftwareComponentArtifactRequest, SoftwareComponentArtifactStub>(
//        resolver
//    ) {
//    override suspend fun cache(
//        request: SoftwareComponentArtifactRequest,
//        repository: SoftwareComponentRepositorySettings
//    ): JobResult<Unit, ArchiveLoadException> {
////        val desc by request::descriptor
//        val resolver = SoftwareComponentRepositoryFactory.createContext(repository)
//
////            if (!graph.contains(desc)) {
////                if (!store.contains(desc)) {
//        val artifact = resolver.getAndResolve(request)
//            .asOutput()
//            .mapFailure(ArchiveLoadException::ArtifactLoadException)
//
//        if (artifact.wasFailure()) return JobResult.Failure(artifact.failureOrNull()!!)
//
//        return withWeight(1) {
//            cache(artifact.orNull()!!)
//        }
////                }
////            }
////            return Success(Unit)
//    }

//        private fun <S : RepositorySettings, R : ArtifactRequest<*>> DependencyGraphProvider<R, S>.cacheArtifact(
//                pSettings: Map<String, String>,
//                pRequest: Map<String, String>,
//        ): Either<ArchiveLoadException, Unit> = either.eager {
//            val settings = parseSettings(pSettings)
//                    ?: shift(ArchiveLoadException.DependencyInfoParseFailed("Failed to parse artifact repository settings: '$pSettings'"))
//
//            val request = parseRequest(pRequest)
//                    ?: shift(ArchiveLoadException.DependencyInfoParseFailed("Failed to parse artifact request: '$pRequest'"))
//
//            val loader = graph.cacherOf(settings)
////                    ?: shift(ArchiveLoadException.IllegalState("Dependency graph loader is not a DependencyLoader."))
//
//            loader.cache(request).bind()
//        }

//    private suspend fun cache(artifact: Artifact): JobResult<Unit, ArchiveLoadException> =
//        job(JobName("Cache software component: '${artifact.metadata.descriptor}'")) {
//            val metadata = artifact.metadata
//            check(metadata is SoftwareComponentArtifactMetadata) { "Invalid artifact metadata! Must be plugin artifact metadata." }
//
//            if (store.contains(metadata.descriptor)) return@job
//
//            val jarPath = metadata.resource?.let {
//                val descriptor by metadata::descriptor
//
//                val jarName = "${descriptor.artifact}-${descriptor.version}.jar"
//                val jarPath = path resolve descriptor.group.replace(
//                    '.',
//                    File.separatorChar
//                ) resolve descriptor.artifact resolve descriptor.version resolve jarName
//
//                if (!Files.exists(jarPath)) {
//                    info("Downloading software component: '$descriptor'")
//
//                    Channels.newChannel(it.open()).use { cin ->
//                        jarPath.make()
//                        FileOutputStream(jarPath.toFile()).use { fout: FileOutputStream ->
//                            fout.channel.transferFrom(cin, 0, Long.MAX_VALUE)
//                        }
//                    }
//                }
//
//                jarPath
//            }
//
//            artifact.children
//                .map {
//                    it.orNull()
//                        ?: throw IllegalStateException("Found a artifact stub: '${(it as Either.Left).value}' when trying to cache plugins.")
//                }
//                .map {
//                    async {
//                        withWeight(1) {
//                            cache(it)
//                        }
//                    }
//                }.forEach { it.await().attempt() }
//
//            metadata.dependencies.map { i ->
//                val provider = dependencyProviders.get(i.type)
//                    ?: throw IllegalArgumentException("Invalid repository: '${i.type}'. Failed to find provider for this type.")
//
//                withWeight(1) {
//                    async {
//                        provider.cacheArtifact(
//                            i.repositorySettings,
//                            i.request
//                        )
//                    }
//                }
//            }.forEach { it.await().attempt() }
//
//            val data = SoftwareComponentData(
//                metadata.descriptor,
//                jarPath,
//                metadata.children
//                    .map(SoftwareComponentChildInfo::descriptor),
//                metadata.dependencies.map {
//                    SoftwareComponentDependencyData(it.type, it.request)
//                },
//                metadata.runtimeModel,
//            )
//
//            store.put(metadata.descriptor, data)
//        }


}
//}