package net.yakclient.boot

import arrow.core.Either
import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.artifact.resolver.simple.maven.*
import kotlinx.cli.*
import net.yakclient.archives.*
import net.yakclient.boot.archive.ArchiveKey
import net.yakclient.boot.archive.BasicArchiveResolutionProvider
import net.yakclient.boot.archive.moduleNameFor
import net.yakclient.boot.component.*
import net.yakclient.boot.component.SoftwareComponentModelRepository.Companion.DEFAULT
import net.yakclient.boot.component.SoftwareComponentModelRepository.Companion.LOCAL
import net.yakclient.boot.component.artifact.SoftwareComponentArtifactRequest
import net.yakclient.boot.component.artifact.SoftwareComponentDescriptor
import net.yakclient.boot.component.artifact.SoftwareComponentRepositorySettings
import net.yakclient.boot.dependency.*
import net.yakclient.boot.maven.MavenDataAccess
import net.yakclient.boot.maven.MavenDependencyGraph
import net.yakclient.boot.store.CachingDataStore
import net.yakclient.boot.util.toSafeResource
import java.io.File
import java.nio.file.Path
import java.security.Policy
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.collections.HashSet

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
                withBootDependencies(),
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
                withBootDependencies(),
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
                {},
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
    init: (MavenPopulateContext.(String) -> Boolean) -> Unit,
    mavenCache: String
) = bootContext.dependencyProviders.add(
    createMavenProvider(mavenCache, init)
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

public fun withBootDependencies(init: (MavenPopulateContext.(String) -> Boolean) -> Unit = {}): (MavenPopulateContext.(String) -> Boolean) -> Unit =
    { populateFrom ->
        val mavenCentral = SimpleMaven.createContext(
            SimpleMavenRepositorySettings.mavenCentral(
                preferredHash = HashType.SHA1
            )
        )

        val yakCentral = SimpleMaven.createContext(
            SimpleMavenRepositorySettings.default(
                "http://repo.yakclient.net/snapshots",
                preferredHash = HashType.SHA1
            )
        )

        val mavenLocal = SimpleMaven.createContext(
            SimpleMavenRepositorySettings.local()
        )

        val allRepos = listOf(mavenCentral, yakCentral, mavenLocal);

        { dependency: String ->
            allRepos.find { it.populateFrom(dependency) }
        }.also { implementation ->
            implementation("org.jetbrains.kotlin:kotlin-stdlib:1.7.10")
            implementation("io.arrow-kt:arrow-core:1.1.2")

            implementation("com.durganmcbroom:event-api:1.0-SNAPSHOT")
            implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.5")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
            implementation("net.yakclient:archives:1.0-SNAPSHOT")
            implementation("com.durganmcbroom:artifact-resolver:1.0-SNAPSHOT")
            implementation("com.durganmcbroom:artifact-resolver-simple-maven:1.0-SNAPSHOT")
            implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.13.4")
            implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.4")
            implementation("net.yakclient:common-util:1.0-SNAPSHOT")
            implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.22")
        }

        init(populateFrom)
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
    initDependencies: (MavenPopulateContext.(String) -> Boolean) -> Unit,
): DependencyGraphProvider<*, *> {
    val dependencyGraph = createMavenDependencyGraph(cacheLocation, initDependencies)

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

internal fun createMavenDependencyGraph(
    cachePath: String,
    initDependencies: (MavenPopulateContext.(String) -> Boolean) -> Unit,
): MavenDependencyGraph {
    val moduleAwareGraph = populateDependenciesSafely(initDependencies)

    val basePath = Path.of(cachePath)
    val graph = MavenDependencyGraph(
        basePath,
        CachingDataStore(
            MavenDataAccess(basePath)
        ),
        BasicArchiveResolutionProvider(
            Archives.Finders.JPM_FINDER as ArchiveFinder<ArchiveReference>,
            Archives.Resolvers.JPM_RESOLVER
        ),
        moduleAwareGraph
    )

    return graph
}

public fun populateDependenciesSafely(
    initDependencies: (MavenPopulateContext.(String) -> Boolean) -> Unit,
): MutableMap<ArchiveKey<SimpleMavenArtifactRequest>, DependencyNode> {
    class UnVersionedArchiveKey(request: SimpleMavenArtifactRequest) :
        ArchiveKey<SimpleMavenArtifactRequest>(request) {
        val group by request.descriptor::group
        val artifact by request.descriptor::artifact
        val classifier by request.descriptor::classifier

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is UnVersionedArchiveKey) return false

            if (group != other.group) return false
            if (artifact != other.artifact) return false
            if (classifier != other.classifier) return false

            return true
        }

        override fun hashCode(): Int {
            var result = group.hashCode()
            result = 31 * result + artifact.hashCode()
            result = 31 * result + (classifier?.hashCode() ?: 0)
            return result
        }
    }

    val delegate = HashMap<ArchiveKey<SimpleMavenArtifactRequest>, DependencyNode>()
    val moduleAwareGraph: MutableMap<ArchiveKey<SimpleMavenArtifactRequest>, DependencyNode> =
        object : MutableMap<ArchiveKey<SimpleMavenArtifactRequest>, DependencyNode> by delegate {
            override fun get(key: ArchiveKey<SimpleMavenArtifactRequest>): DependencyNode? {
                return delegate[UnVersionedArchiveKey(key.request)] ?: delegate[key]
            }
        }

    fun MavenPopulateContext.populate(
        ref: SimpleMavenArtifactReference,
        request: SimpleMavenArtifactRequest,
    ): DependencyNode {
        val metadata = ref.metadata

        val children = ref.children
            .map { resolverContext.stubResolver.resolve(it) to it.request }
            .mapNotNull { it.first.orNull()?.to(it.second) }
            .mapTo(HashSet()) { populate(it.first, it.second) }

        val nameOr = metadata.resource?.let { moduleNameFor(it.toSafeResource(), metadata.descriptor.artifact) }
        val moduleHandle =
            nameOr?.let(ModuleLayer.boot()::findModule)?.orElseGet { null }?.let(JpmArchives::moduleToArchive)

        val node = DependencyNode(
            moduleHandle,
            children
        )

        delegate[UnVersionedArchiveKey(request)] = node

        return node
    }

    fun MavenPopulateContext.populateFrom(
        request: SimpleMavenArtifactRequest,
    ): Boolean {
        val repo = repositoryContext.artifactRepository
        val ref = repo.get(request).orNull() ?: return false

        populate(ref, request)

        return true
    }

    initDependencies { desc ->
        populateFrom(
            SimpleMavenArtifactRequest(
                desc,
                includeScopes = setOf("compile", "runtime", "import")
            )
        )
    }

    return moduleAwareGraph
}