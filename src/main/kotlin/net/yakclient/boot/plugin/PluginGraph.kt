package net.yakclient.boot.plugin

import arrow.core.Either
import arrow.core.continuations.either
import arrow.core.continuations.ensureNotNull
import arrow.core.identity
import arrow.core.left
import arrow.core.right
import com.durganmcbroom.artifact.resolver.*
import net.yakclient.archives.ArchiveHandle
import net.yakclient.boot.archive.ArchiveGraph
import net.yakclient.boot.archive.ArchiveLoadException
import net.yakclient.boot.archive.ArchiveResolutionProvider
import net.yakclient.boot.dependency.*
import net.yakclient.boot.plugin.artifact.*
import net.yakclient.boot.store.DataStore
import net.yakclient.common.util.make
import net.yakclient.common.util.resolve
import java.io.File
import java.io.FileOutputStream
import java.nio.channels.Channels
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Level

public class PluginGraph(
    private val path: Path,
    private val store: DataStore<PluginDescriptor, PluginData>,
    private val resolutionProvider: ArchiveResolutionProvider<*>,
) : ArchiveGraph<PluginArtifactRequest, PluginNode, PluginRepositorySettings>(BootPlugins) {
    private val mutableGraph: MutableMap<PluginDescriptor, PluginNode> = HashMap()
    override val graph: Map<PluginDescriptor, PluginNode>
        get() = mutableGraph.toMap()

    override fun get(request: PluginArtifactRequest): Either<ArchiveLoadException, PluginNode> {
        return graph[request.descriptor]?.right() ?: either.eager {
            val data = ensureNotNull(store[request.descriptor]) { ArchiveLoadException.ArtifactNotCached }

            load(data).bind()
        }
    }

    override fun loaderOf(settings: PluginRepositorySettings): ArchiveLoader<*> = PluginLoader(
        BootPlugins.createContext(settings)
    )

    private fun <S : RepositorySettings, R : ArtifactRequest<*>> DependencyGraphProvider<R, S>.getArtifact(
        pRequest: Map<String, String>,
    ): Either<ArchiveLoadException, DependencyNode> = either.eager {
        val request = parseRequest(pRequest) ?: shift(ArchiveLoadException.DependencyInfoParseFailed)

        graph.get(request).bind()
    }


    private fun load(data: PluginData): Either<ArchiveLoadException, PluginNode> {
        return graph[data.key]?.right() ?: either.eager {
            val children = data.children.asSequence()
                .map(store::get)
                .map { it?.right() ?: ArchiveLoadException.ArtifactNotCached.left() }
                .map { it.map(::load) }
                .map {
                    it.orNull()?.orNull()
                        ?: throw IllegalStateException("Some children of plugin: '${data.key}' could not be found in the cache. This means your plugin cache has been invalidated and you should delete the directory.")
                }

            val dependencies = data.dependencies.map {
                DependencyProviders.getByType(it.type)?.getArtifact(it.request)?.bind() ?: shift(
                    ArchiveLoadException.DependencyTypeNotFound(it.type)
                )
            }

            val result = data.archive?.let {
                val parents = children.mapNotNullTo(HashSet(), PluginNode::archive) + dependencies.mapNotNullTo(
                    HashSet(),
                    DependencyNode::archive
                )
                resolutionProvider.resolve(
                    it,
                    { a -> PluginClassLoader(a, parents) },
                    parents
                )
            }?.bind()

            val archive = result?.archive

            PluginNode(
                archive,
                children.toSet(),
                dependencies.toSet(),
                data.runtimeModel,
                archive?.let { loadPlugin(it, data.runtimeModel) }
            ).also { mutableGraph[data.key] = it }
        }
    }

    private fun loadPlugin(archive: ArchiveHandle, runtimeModel: PluginRuntimeModel): BootPlugin =
        archive.classloader.loadClass(runtimeModel.entrypoint).getConstructor().newInstance() as BootPlugin

    private inner class PluginLoader(
        override val resolver: ResolutionContext<PluginArtifactRequest, PluginArtifactStub, ArtifactReference<*, PluginArtifactStub>>,
    ) : ArchiveLoader<PluginArtifactStub>(
        resolver
    ) {
        override fun load(
            request: PluginArtifactRequest,
        ): Either<ArchiveLoadException, PluginNode> = either.eager {
            val desc by request::descriptor

            graph[desc] ?: either.eager {
                val data = store[desc] ?: either.eager {
                    val artifact = resolver.getAndResolve(request)
                        .mapLeft(ArchiveLoadException::ArtifactLoadException)
                        .bind()

                    cache(artifact)

                    store[desc]!!
                }.bind()

                load(data).bind()
            }.bind()
        }


        private fun <S : RepositorySettings, R : ArtifactRequest<*>> DependencyGraphProvider<R, S>.cacheArtifact(
            pSettings: Map<String, String>,
            pRequest: Map<String, String>,
        ): Either<ArchiveLoadException, DependencyData<R>> = either.eager {
            val settings = parseSettings(pSettings) ?: shift(ArchiveLoadException.DependencyInfoParseFailed)

            val request = parseRequest(pRequest) ?: shift(ArchiveLoadException.DependencyInfoParseFailed)

            val loader = graph.loaderOf(settings) as? DependencyGraph.DependencyLoader
                ?: shift(ArchiveLoadException.IllegalState("Dependency graph loader is not a DependencyLoader."))

            loader.cache(request).bind()
        }


        private fun cache(artifact: Artifact) {
            val metadata = artifact.metadata
            check(metadata is PluginArtifactMetadata) { "Invalid artifact metadata! Must be plugin artifact metadata." }

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
                .map { it.orNull() ?: throw IllegalStateException("Found a artifact stub: '${(it as Either.Left).value}' when trying to cache plugins.") }
                .forEach(::cache)

            metadata.dependencies.forEach { i ->
                val provider = DependencyProviders.getByType(i.type)
                    ?: throw IllegalArgumentException("Invalid repository: '${i.type}'. Failed to find provider for this type.")
                provider.cacheArtifact(
                    i.repositorySettings,
                    i.request
                ).fold({throw it}, ::identity)
            }

            val data = PluginData(
                metadata.descriptor,
                jarPath,
                metadata.children
                    .map(PluginChildInfo::descriptor),
                metadata.dependencies.map {

                    PluginDependencyData(it.type, it.request)
                },
                metadata.runtimeModel
            )

            artifact.children
                .mapNotNull { it.orNull() }
                .map(::cache)

            store.put(metadata.descriptor, data)
        }
    }
}