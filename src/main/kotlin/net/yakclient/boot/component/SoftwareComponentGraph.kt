package net.yakclient.boot.component

import arrow.core.Either
import arrow.core.continuations.either
import arrow.core.continuations.ensureNotNull
import arrow.core.left
import arrow.core.right
import com.durganmcbroom.artifact.resolver.*
import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.zip.classLoaderToArchive
import net.yakclient.boot.BootInstance
import net.yakclient.boot.archive.ArchiveGraph
import net.yakclient.boot.archive.ArchiveLoadException
import net.yakclient.boot.archive.ArchiveResolutionProvider
import net.yakclient.boot.util.bindMap
import net.yakclient.boot.dependency.*
import net.yakclient.boot.component.artifact.*
import net.yakclient.boot.store.DataStore
import net.yakclient.common.util.make
import net.yakclient.common.util.resolve
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Constructor
import java.nio.channels.Channels
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Level


// TODO Check for cyclic plugins
public open class SoftwareComponentGraph (
        private val path: Path,
        private val store: DataStore<SoftwareComponentDescriptor, SoftwareComponentData>,
        private val resolutionProvider: ArchiveResolutionProvider<*>,
        private val dependencyProviders: DependencyTypeContainer,
        private val bootInstance: BootInstance,
        private val mutableGraph: MutableMap<SoftwareComponentDescriptor, SoftwareComponentNode> = HashMap()
) : ArchiveGraph<SoftwareComponentDescriptor, SoftwareComponentNode, SoftwareComponentRepositorySettings>(SoftwareComponentRepositoryFactory) {
    override val graph: Map<SoftwareComponentDescriptor, SoftwareComponentNode>
        get() = mutableGraph.toMap()


    override fun get(descriptor: SoftwareComponentDescriptor): Either<ArchiveLoadException, SoftwareComponentNode> {
        return graph[descriptor]?.right() ?: either.eager {
            val data = ensureNotNull(store[descriptor]) { ArchiveLoadException.ArtifactNotCached }

            load(data).bind()
        }
    }

    override fun cacherOf(settings: SoftwareComponentRepositorySettings): SoftwareComponentCacher = SoftwareComponentCacher(
            SoftwareComponentRepositoryFactory.createContext(settings)
    )

    override fun isCached(descriptor: SoftwareComponentDescriptor): Boolean {
        return store.contains(descriptor)
    }

    private fun <S : RepositorySettings, K: ArtifactMetadata.Descriptor, R : ArtifactRequest<K>> DependencyGraphProvider<K, R, S>.getArtifact(
            pRequest: Map<String, String>,
    ): Either<ArchiveLoadException, DependencyNode> = either.eager {
        val request: R = parseRequest(pRequest)
                ?: shift(ArchiveLoadException.DependencyInfoParseFailed("Failed to parse artifact request: '$pRequest'"))

        graph.get(request.descriptor).bind()
    }

    private fun load(data: SoftwareComponentData): Either<ArchiveLoadException, SoftwareComponentNode> {
        return graph[data.key]?.right() ?: either.eager {
            val children = data.children
                    .map(store::get)
                    .map { it?.right() ?: ArchiveLoadException.ArtifactNotCached.left() }
                    .map { it.map(::load) }
                    .map {
                        it.orNull()
                                ?: throw IllegalStateException("Some children of plugin: '${data.key}' could not be found in the cache. This means your plugin cache has been invalidated and you should delete the directory.")
                    }.bindMap().bind()

            val dependencies = data.dependencies.map {
                dependencyProviders.get(it.type)?.getArtifact(it.request)?.bind() ?: shift(
                        ArchiveLoadException.DependencyTypeNotFound(it.type)
                )
            }

            val result = data.archive?.let {
                val parents = children.mapNotNullTo(HashSet(), SoftwareComponentNode::archive) + dependencies.mapNotNullTo(
                        HashSet(),
                        DependencyNode::archive
                ) + classLoaderToArchive(this::class.java.classLoader)
                resolutionProvider.resolve(
                        it,
                        { a -> SofwareComponentClassLoader(this::class.java.classLoader, a, parents) },
                        parents
                )
            }?.bind()

            val archive = result?.archive

            SoftwareComponentNode(
                    data.key,
                    archive,
                    children.toSet(),
                    dependencies.toSet(),
                    data.runtimeModel,
                    archive?.let { loadFactory(it, data.runtimeModel) },
            ).also { mutableGraph[data.key] = it }
        }
    }

    private fun <T : Any> Class<T>.tryGetConstructor(vararg params: Class<*>): Constructor<T>? = net.yakclient.common.util.runCatching(NoSuchMethodException::class) { this.getConstructor(*params) }


    private fun loadFactory(archive: ArchiveHandle, runtimeModel: SoftwareComponentModel): ComponentFactory<*, *> {
        val loadClass = archive.classloader.loadClass(runtimeModel.entrypoint)
        return (loadClass.tryGetConstructor(BootInstance::class.java)?.newInstance(bootInstance)
                ?: loadClass.tryGetConstructor()?.newInstance()) as ComponentFactory<*, *>
    }

    public inner class SoftwareComponentCacher(
            override val resolver: ResolutionContext<SoftwareComponentArtifactRequest, SoftwareComponentArtifactStub, ArtifactReference<*, SoftwareComponentArtifactStub>>,
    ) : ArchiveCacher<SoftwareComponentArtifactRequest, SoftwareComponentArtifactStub>(
            resolver
    ) {
        override fun cache(
                request: SoftwareComponentArtifactRequest,
        ): Either<ArchiveLoadException, Unit> = either.eager {
            val desc by request::descriptor

            if (!graph.contains(desc)) {
                if (!store.contains(desc)) {
                    val artifact = resolver.getAndResolve(request)
                            .mapLeft(ArchiveLoadException::ArtifactLoadException)
                            .bind()

                    cache(artifact)
                }
            }
        }

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


        private fun cache(artifact: Artifact) {
            val metadata = artifact.metadata
            check(metadata is SoftwareComponentArtifactMetadata) { "Invalid artifact metadata! Must be plugin artifact metadata." }

            if (store.contains(metadata.descriptor)) return

            val jarPath = metadata.resource?.let {
                val descriptor by metadata::descriptor

                val jarName = "${descriptor.artifact}-${descriptor.version}.jar"
                val jarPath = path resolve descriptor.group.replace(
                        '.',
                        File.separatorChar
                ) resolve descriptor.artifact resolve descriptor.version resolve jarName

                if (!Files.exists(jarPath)) {
                    logger.log(Level.INFO, "Downloading dependency: '$descriptor'")

                    Channels.newChannel(it.open()).use { cin ->
                        jarPath.make()
                        FileOutputStream(jarPath.toFile()).use { fout: FileOutputStream ->
                            fout.channel.transferFrom(cin, 0, Long.MAX_VALUE)
                        }
                    }
                }

                jarPath
            }

            artifact.children
                    .map {
                        it.orNull()
                                ?: throw IllegalStateException("Found a artifact stub: '${(it as Either.Left).value}' when trying to cache plugins.")
                    }
                    .forEach(::cache)

            metadata.dependencies.forEach { i ->
                val provider = dependencyProviders.get(i.type)
                        ?: throw IllegalArgumentException("Invalid repository: '${i.type}'. Failed to find provider for this type.")
                provider.cacheArtifact(
                        i.repositorySettings,
                        i.request
                ).tapLeft { throw it }
            }

            val data = SoftwareComponentData(
                    metadata.descriptor,
                    jarPath,
                    metadata.children
                            .map(SoftwareComponentChildInfo::descriptor),
                    metadata.dependencies.map {
                        SoftwareComponentDependencyData(it.type, it.request)
                    },
                    metadata.runtimeModel,
            )

            artifact.children
                    .mapNotNull { it.orNull() }
                    .map(::cache)

            store.put(metadata.descriptor, data)
        }
    }
}