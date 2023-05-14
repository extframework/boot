package net.yakclient.boot

import arrow.core.Either
import com.durganmcbroom.artifact.resolver.ArtifactReference
import com.durganmcbroom.artifact.resolver.ArtifactStub
import com.durganmcbroom.artifact.resolver.ResolutionContext
import com.durganmcbroom.artifact.resolver.simple.maven.*
import kotlinx.cli.*
import net.yakclient.archives.*
import net.yakclient.archives.zip.ZipResolutionResult
import net.yakclient.boot.archive.ArchiveLoadException
import net.yakclient.boot.archive.BasicArchiveResolutionProvider
import net.yakclient.boot.component.ComponentContext
import net.yakclient.boot.component.SoftwareComponentDataAccess
import net.yakclient.boot.component.SoftwareComponentGraph
import net.yakclient.boot.component.SoftwareComponentModelRepository.Companion.DEFAULT
import net.yakclient.boot.component.SoftwareComponentModelRepository.Companion.LOCAL
import net.yakclient.boot.component.SoftwareComponentNode
import net.yakclient.boot.component.artifact.SoftwareComponentArtifactRequest
import net.yakclient.boot.component.artifact.SoftwareComponentDescriptor
import net.yakclient.boot.component.artifact.SoftwareComponentRepositorySettings
import net.yakclient.boot.dependency.DependencyGraph
import net.yakclient.boot.dependency.DependencyGraphProvider
import net.yakclient.boot.dependency.DependencyProviders
import net.yakclient.boot.maven.MavenDataAccess
import net.yakclient.boot.maven.MavenDependencyGraph
import net.yakclient.boot.store.CachingDataStore
import java.io.File
import java.nio.file.Path
import java.util.logging.Level
import java.util.logging.Logger

public data class BootInstance(
    val dependencyProviders: DependencyProviders,
    val mavenCache: String,
    val softwareComponentCache: String,
) {
    private val softwareComponentGraph by lazy {
        initSoftwareComponentGraph(
            softwareComponentCache,
            dependencyProviders
        )
    }

    init {
        initMaven(
            dependencyProviders,
            mavenCache
        )
    }

    @Throws(ArchiveLoadException::class)
    public fun cache(
        request: SoftwareComponentArtifactRequest,
        location: SoftwareComponentRepositorySettings,
        configuration: Map<String, String>
    ): Boolean {
        val cacher = softwareComponentGraph.cacherOf(
            location,
        )

        val cacheAttempt = cacher.cache(
            request
        )
        val success = cacheAttempt.tapLeft { throw it }.isRight()

        return success && softwareComponentGraph.cacheConfiguration(request.descriptor, configuration)
    }

    @Throws(ArchiveLoadException::class)
    public fun startAll(
        components: List<SoftwareComponentArtifactRequest>
    ) {
        val nodes: List<SoftwareComponentNode> = components.map { req ->
            val get = softwareComponentGraph.get(req)

            if (get is Either.Right) {
                get.value
            } else {
                throw (get as Either.Left).value
            }
        }

        val enabled = HashSet<SoftwareComponentDescriptor>()

        nodes.forEach {
            enabled.addAll(enableAllComponents(it, this, enabled).map(SoftwareComponentNode::descriptor))
        }
    }

    public fun configure(
        descriptor: SoftwareComponentDescriptor,
        configuration: Map<String, String>
    ): Boolean = softwareComponentGraph.cacheConfiguration(
        descriptor,
        configuration
    )

    public companion object {
        public fun new(base: String) : BootInstance {
            return BootInstance(
                DependencyProviders(),
                "$base/maven",
                "$base/components"
            )
        }
    }
}

@ExperimentalCli
public fun main(args: Array<String>) {
    // Setup logger
    val logger = Logger.getLogger("boot")

    // Setup argument parser
    val parser = ArgParser("boot")

    // Get working dir
    val workingDir = System.getProperty("user.dir")

    // Parse args
    val mavenCache by parser.option(ArgType.String, "maven-cache-location")
        .default("$workingDir${File.separator}cache${File.separator}maven")

    val softwareComponentCache by parser.option(ArgType.String, "software-component-cache-location")
        .default("$workingDir${File.separator}cache${File.separator}software-component")

    // Create Boot context for later use
    val boot by lazy { BootInstance(
        DependencyProviders(),
        mavenCache, softwareComponentCache
    ) }

    fun echo(value: String) = logger.log(Level.INFO, value)

    // Start of cli commands
    class CacheComponent : Subcommand(
        "cache",
        "Installs a single software component into the cache for later use."
    ) {
        val descriptor by option(ArgType.String, "descriptor").required()
        val location by option(ArgType.String, "location").required()
        val type by option(ArgType.String, "type").default(DEFAULT)
        val configuration by option(
            ArgType.String,
            "configuration",
            "Configures the given component. Should be in a map format where the separator is a ',' and key value pairs are assigned with '='. Example 'one=1,two=2,three=3'"
        ).default("")

        override fun execute() {
            echo("Setting up maven")

            val settings = when (type) {
                DEFAULT -> SoftwareComponentRepositorySettings.default(
                    location,
                    preferredHash = HashType.SHA1
                )

                LOCAL -> SoftwareComponentRepositorySettings.local(
                    location,
                    preferredHash = HashType.SHA1
                )

                else -> throw IllegalArgumentException("Unknown Software Component repository type: '$type'. Only known types are '$DEFAULT' (for remote repositories) and '$LOCAL' (for local repositories)")
            }

            val request = SoftwareComponentArtifactRequest(
                descriptor,
            )

            val configuration = if (configuration.isNotBlank()) {
                echo("Parsing and setting configuration for component '$descriptor'")

                configuration
                    .split(",")
                    .associate {
                        val split = it.split("=")

                        split[0] to split.getOrElse(1) { "" }
                    }
            } else HashMap()

            try {
                boot.cache(request, settings, configuration)
                echo("Successfully cached the component: '$descriptor'!")
            } catch (ex: ArchiveLoadException) {
                echo("Failed to cache component, an error occurred. Throwing.")
                throw ex
            }
        }
    }

    class StartComponents : Subcommand(
        "start",
        "Starts any given number of components and their children."
    ) {
        val components by argument(ArgType.String).vararg()

        override fun execute() {
            echo("Loading components: '${components.joinToString()}'")

            boot.startAll(
                components.map { SoftwareComponentArtifactRequest(it) }
            )
        }
    }

    class Configure : Subcommand(
        "configure",
        "(Re)Configures a component"
    ) {
        val descriptor by option(
            ArgType.String,
            "descriptor",
            description = "The descriptor of the component to configure"
        ).required()
        val configuration by option(ArgType.String, "configuration").required()

        override fun execute() {
            val config = if (configuration.isNotBlank())
                configuration
                    .split(",")
                    .associate {
                        val split = it.split("=")

                        split[0] to split.getOrElse(1) { "" }
                    } else mapOf()

            echo("Caching")
            val success = boot.configure(
                SoftwareComponentDescriptor.parseDescription(descriptor)
                    ?: throw IllegalArgumentException("Failed to parse descriptor: '$descriptor'"),
                config
            )

            if (success) echo("Successfully cached configuration for component '$descriptor'")
            else echo("Error, unable to cache configuration for component: '$descriptor'. Make sure that you have already cached the component itself and try again.")
        }
    }

    parser.subcommands(CacheComponent(), StartComponents(), Configure())

    parser.parse(args)
}

// Convenience methods
private fun initMaven(
    dependencyProviders: DependencyProviders,
    mavenCache: String
) = dependencyProviders.add(
    createMavenProvider(mavenCache)
)



public fun enableAllComponents(
    node: SoftwareComponentNode,
    boot: BootInstance,
    alreadyEnabled: Set<SoftwareComponentDescriptor> = HashSet(),
): Set<SoftwareComponentNode> {
    val enabledChildren = node.children.flatMapTo(HashSet()) { enableAllComponents(it,boot, alreadyEnabled ) }

    val enabled = if (!alreadyEnabled.contains(node.descriptor)) node.component?.onEnable(
        ComponentContext(
            node.configuration,
            boot
        )
    ) != null else false

    return if (enabled) enabledChildren + node else enabledChildren
}

private fun initSoftwareComponentGraph(
    cache: String,
    dependencyProviders: DependencyProviders
): SoftwareComponentGraph {
    val cachePath = Path.of(cache)

    return SoftwareComponentGraph(
        cachePath,
        CachingDataStore(SoftwareComponentDataAccess(cachePath)),
        BasicArchiveResolutionProvider(
            Archives.Finders.ZIP_FINDER as ArchiveFinder<ArchiveReference>,
            Archives.Resolvers.ZIP_RESOLVER
        ),
        dependencyProviders
    )
}

public fun createMavenProvider(
    cacheLocation: String,
): DependencyGraphProvider<*, *> {
    val dependencyGraph = createMavenDependencyGraph(cacheLocation)

    return object : DependencyGraphProvider<SimpleMavenArtifactRequest, SimpleMavenRepositorySettings> {
        override val name: String = "simple-maven"
        override val graph: DependencyGraph<SimpleMavenArtifactRequest, *, SimpleMavenRepositorySettings> =
            dependencyGraph

        override fun parseRequest(request: Map<String, String>): SimpleMavenArtifactRequest? {
            val descriptorName = request["descriptor"] ?: return null
            val isTransitive = request["isTransitive"] ?: "true"
            val scopes = request["includeScopes"] ?: "compile,runtime,import"
            val excludeArtifacts = request["excludeArtifacts"]

            return SimpleMavenArtifactRequest(
                SimpleMavenDescriptor.parseDescription(descriptorName) ?: return null,
                isTransitive.toBoolean(),
                scopes.split(',').toSet(),
                excludeArtifacts?.split(',')?.toSet() ?: setOf()
            )
        }

        override fun parseSettings(settings: Map<String, String>): SimpleMavenRepositorySettings? {
            val releasesEnabled = settings["releasesEnabled"] ?: "true"
            val snapshotsEnabled = settings["snapshotsEnabled"] ?: "true"
            val location = settings["location"] ?: return null
            val preferredHash = settings["preferredHash"] ?: "SHA1"
            val type = settings["type"] ?: "default"

            val hashType = HashType.valueOf(preferredHash)

            return when (type) {
                "default" -> SimpleMavenRepositorySettings.default(
                    location,
                    releasesEnabled.toBoolean(),
                    snapshotsEnabled.toBoolean(),
                    hashType
                )

                "local" -> SimpleMavenRepositorySettings.local(location, hashType)
                else -> return null
            }
        }
    }
}

public typealias MavenPopulateContext = ResolutionContext<SimpleMavenArtifactRequest, ArtifactStub<SimpleMavenArtifactRequest, SimpleMavenRepositoryStub>, ArtifactReference<SimpleMavenArtifactMetadata, ArtifactStub<SimpleMavenArtifactRequest, SimpleMavenRepositoryStub>>>

private data class BasicResolutionResult(override val archive: ArchiveHandle) : ResolutionResult

private const val DASH_DOT_PATTERN = "-(\\d+(\\.|$))"

internal fun createMavenDependencyGraph(
    cachePath: String,
): MavenDependencyGraph {
    val resolutionProvider = object : BasicArchiveResolutionProvider<ArchiveReference, ZipResolutionResult>(
        Archives.Finders.ZIP_FINDER as ArchiveFinder<ArchiveReference>,
        Archives.Resolvers.ZIP_RESOLVER,
    ) {}
    val basePath = Path.of(cachePath)
    val graph = MavenDependencyGraph(
        basePath,
        CachingDataStore(
            MavenDataAccess(basePath)
        ),
        resolutionProvider
//         HashMap<ArchiveKey<SimpleMavenArtifactRequest>, DependencyNode>(),
    )
//        object : BasicArchiveResolutionProvider<ArchiveReference, BasicResolutionResult>(
//            Archives.Finders.ZIP_FINDER as ArchiveFinder<ArchiveReference>,
//            object : ArchiveResolver<ArchiveReference, BasicResolutionResult> {
//                private val delegate = Archives.Resolvers.ZIP_RESOLVER
//                override val type: KClass<ArchiveReference> by delegate::type
//
//                override fun resolve(
//                    archiveRefs: List<ArchiveReference>,
//                    clProvider: ClassLoaderProvider<ArchiveReference>,
//                    parents: Set<ArchiveHandle>
//                ): List<BasicResolutionResult> = delegate.resolve(archiveRefs, clProvider, parents).map {
//                    BasicResolutionResult(it.archive)
//                }
//            }
//        ) {
//            override fun resolve(
//                resource: Path,
//                classLoader: ClassLoaderProvider<ArchiveReference>,
//                parents: Set<ArchiveHandle>
//            ): Either<ArchiveLoadException, BasicResolutionResult> {
//                val fileName = resource.fileName.toString()
//                val artifactName =
//                    fileName.substring(0, Regex(DASH_DOT_PATTERN).find(fileName)?.range?.first ?: (fileName.length))
//
//                val name = moduleNameFor(LocalResource(resource.toUri()), artifactName)
//
//                val maybeModule = ModuleLayer.boot().findModule(name).orElseGet { null }
//
//                return maybeModule
//                    ?.let(JpmArchives::moduleToArchive)
//                    ?.let(::BasicResolutionResult)
//                    ?.right() ?: super.resolve(resource, classLoader, parents)
//            }
//        },

    return graph
}

//public fun populateDependenciesSafely(
//    initDependencies: (MavenPopulateContext.(String) -> Boolean) -> Unit,
//): MutableMap<ArchiveKey<SimpleMavenArtifactRequest>, DependencyNode> {
//    class UnVersionedArchiveKey(request: SimpleMavenArtifactRequest) :
//        ArchiveKey<SimpleMavenArtifactRequest>(request) {
//        val group by request.descriptor::group
//        val artifact by request.descriptor::artifact
//        val classifier by request.descriptor::classifier
//
//        override fun equals(other: Any?): Boolean {
//            if (this === other) return true
//            if (other !is UnVersionedArchiveKey) return false
//
//            if (group != other.group) return false
//            if (artifact != other.artifact) return false
//            if (classifier != other.classifier) return false
//
//            return true
//        }
//
//        override fun hashCode(): Int {
//            var result = group.hashCode()
//            result = 31 * result + artifact.hashCode()
//            result = 31 * result + (classifier?.hashCode() ?: 0)
//            return result
//        }
//    }
//
//    val delegate = HashMap<ArchiveKey<SimpleMavenArtifactRequest>, DependencyNode>()
//    val moduleAwareGraph: MutableMap<ArchiveKey<SimpleMavenArtifactRequest>, DependencyNode> =
//        object : MutableMap<ArchiveKey<SimpleMavenArtifactRequest>, DependencyNode> by delegate {
//            override fun get(key: ArchiveKey<SimpleMavenArtifactRequest>): DependencyNode? {
//                return delegate[UnVersionedArchiveKey(key.request)] ?: delegate[key]
//            }
//        }
//
//    fun MavenPopulateContext.populate(
//        ref: SimpleMavenArtifactReference,
//        request: SimpleMavenArtifactRequest,
//    ): DependencyNode {
//        val metadata = ref.metadata
//
//        val children = ref.children
//            .map { resolverContext.stubResolver.resolve(it) to it.request }
//            .mapNotNull { it.first.orNull()?.to(it.second) }
//            .mapTo(HashSet()) { populate(it.first, it.second) }
//
//        val nameOr = metadata.resource?.let { moduleNameFor(it.toSafeResource(), metadata.descriptor.artifact) }
//        val moduleHandle =
//            nameOr?.let(ModuleLayer.boot()::findModule)?.orElseGet { null }?.let(JpmArchives::moduleToArchive)
//
//        val node = DependencyNode(
//            moduleHandle,
//            children
//        )
//
//        delegate[UnVersionedArchiveKey(request)] = node
//
//        return node
//    }
//
//    fun MavenPopulateContext.populateFrom(
//        request: SimpleMavenArtifactRequest,
//    ): Boolean {
//        val repo = repositoryContext.artifactRepository
//        val ref = repo.get(request).orNull() ?: return false
//
//        populate(ref, request)
//
//        return true
//    }
//
//    initDependencies { desc ->
//        populateFrom(
//            SimpleMavenArtifactRequest(
//                desc,
//                includeScopes = setOf("compile", "runtime", "import")
//            )
//        )
//    }
//
//    return moduleAwareGraph
//}

////module yakclient.boot {
////    requires kotlin.stdlib;
////    requires durganmcbroom.artifact.resolver;
////    requires yakclient.archives;
////    requires java.logging;
////    requires com.fasterxml.jackson.databind;
////    requires com.fasterxml.jackson.kotlin;
////    requires yakclient.common.util;
////    requires durganmcbroom.artifact.resolver.simple.maven;
////    requires kotlinx.cli.jvm;
////    requires arrow.core.jvm;
////    requires jdk.unsupported;
////    requires kotlin.reflect;
////    requires kotlin.stdlib.jdk8;
////
////    opens net.yakclient.boot.dependency to com.fasterxml.jackson.databind, kotlin.reflect;
////    opens net.yakclient.boot to com.fasterxml.jackson.databind, kotlin.reflect, yakclient.boot.test;
////    opens net.yakclient.boot.maven to com.fasterxml.jackson.databind, kotlin.reflect;
////    opens net.yakclient.boot.component to com.fasterxml.jackson.databind, kotlin.reflect;
////
////
////    exports net.yakclient.boot.store;
////    exports net.yakclient.boot;
////    exports net.yakclient.boot.maven;
////    exports net.yakclient.boot.loader;
////    exports net.yakclient.boot.dependency;
////    exports net.yakclient.boot.container;
////    exports net.yakclient.boot.security;
////    exports net.yakclient.boot.component;
////    exports net.yakclient.boot.archive;
////    exports net.yakclient.boot.util;
////    exports net.yakclient.boot.container.volume;
////}