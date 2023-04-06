package net.yakclient.boot

import arrow.core.Either
import arrow.core.right
import com.durganmcbroom.artifact.resolver.ArtifactReference
import com.durganmcbroom.artifact.resolver.ArtifactStub
import com.durganmcbroom.artifact.resolver.ResolutionContext
import com.durganmcbroom.artifact.resolver.simple.maven.*
import kotlinx.cli.*
import net.yakclient.archives.*
import net.yakclient.boot.archive.ArchiveLoadException
import net.yakclient.boot.archive.BasicArchiveResolutionProvider
import net.yakclient.boot.archive.moduleNameFor
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
import net.yakclient.common.util.resource.LocalResource
import java.io.File
import java.nio.file.Path
import java.security.Policy
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.reflect.KClass

@ExperimentalCli
public fun main(args: Array<String>) {
    // Setup security policies
    Policy.setPolicy(ContainerPolicy())
    System.setSecurityManager(SecurityManager())

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
    val bootContext = BootContext()

    fun echo(value: String) = logger.log(Level.INFO, value)

    echo("Setting up Software Component Graph.")
    val softwareComponentGraph by lazy {
        initSoftwareComponentGraph(
            softwareComponentCache,
            bootContext.dependencyProviders
        )
    }

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
            initMaven(
                bootContext,
                mavenCache
            )

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

            echo("Creating a software component cacher")
            val cacher = softwareComponentGraph.cacherOf(
                settings,
            )

            val request = SoftwareComponentArtifactRequest(
                descriptor,
            )

            echo("Caching")
            val cacheAttempt = cacher.cache(
                request
            )

            echo("Successfully cached the component: '$descriptor'!")

            if (cacheAttempt.isLeft()) {
                echo("Failed to cache component: '$descriptor'. Throwing exception.")
                cacheAttempt.tapLeft { throw it }
            }

            if (configuration.isNotBlank()) {
                echo("Parsing and setting configuration for component '$descriptor'")

                val parsedConfig = configuration
                    .split(",")
                    .associate {
                        val split = it.split("=")

                        split[0] to split.getOrElse(1) { "" }
                    }

                softwareComponentGraph.cacheConfiguration(
                    request.descriptor,
                    parsedConfig
                )
            }
        }
    }

    class StartComponents : Subcommand(
        "start",
        "Starts any given number of components from the cache into the current runtime"
    ) {
        val components by argument(ArgType.String).vararg()

        override fun execute() {
            echo("Setting up maven")
            initMaven(
                bootContext,
                mavenCache
            )

            echo("Starting to load components '${components.joinToString()}'")

            val nodes: List<SoftwareComponentNode> = components.map {
                SoftwareComponentArtifactRequest(it)
            }.map { req ->
                echo("Loading component: '$req'")

                val get = softwareComponentGraph.get(req)

                if (get is Either.Right) {
                    echo("Successfully loaded '$req' and all of its children.")
                    get.value
                } else {
                    echo("Failed to load '$req'. This is a fatal exception.")
                    throw (get as Either.Left).value
                }
            }

            val enabled = HashSet<SoftwareComponentDescriptor>()

            nodes.forEach {
                enabled.addAll(enableAllComponents(it, enabled, bootContext).map(SoftwareComponentNode::descriptor))
            }
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
            echo("Setting up maven")
            initMaven(
                bootContext,
                mavenCache
            )

            val config = if (configuration.isNotBlank())
                configuration
                    .split(",")
                    .associate {
                        val split = it.split("=")

                        split[0] to split.getOrElse(1) { "" }
                    } else mapOf()

            echo("Caching")
            softwareComponentGraph.cacheConfiguration(
                SimpleMavenDescriptor.parseDescription(descriptor)
                    ?: throw IllegalArgumentException("Failed to parse descriptor: '$descriptor'"),
                config
            )

            echo("Successfully cached configuration for component '$descriptor'")
        }
    }

    parser.subcommands(CacheComponent(), StartComponents(), Configure())

    parser.parse(args)
}

// Convenience methods
private fun initMaven(
    bootContext: BootContext,
    mavenCache: String
) = bootContext.dependencyProviders.add(
    createMavenProvider(mavenCache)
)

// Boot context
public data class BootContext(
    val dependencyProviders: DependencyProviders = DependencyProviders()
)

public fun enableAllComponents(
    node: SoftwareComponentNode,
    alreadyEnabled: Set<SoftwareComponentDescriptor> = HashSet(),
    boot: BootContext
): Set<SoftwareComponentNode> {
    val enabledChildren = node.children.flatMapTo(HashSet()) { enableAllComponents(it, alreadyEnabled, boot) }

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
            Archives.Finders.JPM_FINDER as ArchiveFinder<ArchiveReference>,
            Archives.Resolvers.JPM_RESOLVER
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
    val basePath = Path.of(cachePath)
    val graph = MavenDependencyGraph(
        basePath,
        CachingDataStore(
            MavenDataAccess(basePath)
        ),
        object : BasicArchiveResolutionProvider<ArchiveReference, BasicResolutionResult>(
            Archives.Finders.JPM_FINDER as ArchiveFinder<ArchiveReference>,
            object : ArchiveResolver<ArchiveReference, BasicResolutionResult> {
                private val delegate = Archives.Resolvers.JPM_RESOLVER
                override val type: KClass<ArchiveReference> by delegate::type

                override fun resolve(
                    archiveRefs: List<ArchiveReference>,
                    clProvider: ClassLoaderProvider<ArchiveReference>,
                    parents: Set<ArchiveHandle>
                ): List<BasicResolutionResult> = delegate.resolve(archiveRefs, clProvider, parents).map {
                    BasicResolutionResult(it.archive)
                }
            }
        ) {
            override fun resolve(
                resource: Path,
                classLoader: ClassLoaderProvider<ArchiveReference>,
                parents: Set<ArchiveHandle>
            ): Either<ArchiveLoadException, BasicResolutionResult> {
                val fileName = resource.fileName.toString()
                val artifactName =
                    fileName.substring(0, Regex(DASH_DOT_PATTERN).find(fileName)?.range?.first ?: (fileName.length))

                val name = moduleNameFor(LocalResource(resource.toUri()), artifactName)

                val maybeModule = ModuleLayer.boot().findModule(name).orElseGet { null }

                return maybeModule
                    ?.let(JpmArchives::moduleToArchive)
                    ?.let(::BasicResolutionResult)
                    ?.right() ?: super.resolve(resource, classLoader, parents)
            }
        },
        HashMap()
    )

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