package net.yakclient.boot

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.artifact.resolver.simple.maven.*
import com.durganmcbroom.artifact.resolver.simple.maven.layout.SimpleMavenDefaultLayout
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.cli.*
import net.yakclient.archives.*
import net.yakclient.boot.archive.ArchiveKey
import net.yakclient.boot.archive.BasicArchiveResolutionProvider
import net.yakclient.boot.archive.moduleNameFor
import net.yakclient.boot.dependency.*
import net.yakclient.boot.maven.MavenDataAccess
import net.yakclient.boot.maven.MavenDependencyGraph
import net.yakclient.boot.component.*
import net.yakclient.boot.component.SoftwareComponentModelRepository.Companion.DEFAULT
import net.yakclient.boot.component.SoftwareComponentModelRepository.Companion.LOCAL
import net.yakclient.boot.component.artifact.SoftwareComponentArtifactRequest
import net.yakclient.boot.component.artifact.SoftwareComponentDescriptor
import net.yakclient.boot.component.artifact.SoftwareComponentRepositorySettings
import net.yakclient.boot.store.CachingDataStore
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.Policy
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

//public object Boot {
//    public var maven: MavenDependencyGraph by immutableLateInit()
//    public val eventManager: EventPipelineManager = EventPipelineManager()
//}
//
//public data class PluginArgument(
//    public val request: PluginArtifactRequest,
//    public val repository: PluginRepositorySettings,
//    public val configuration: Map<String, String>,
//)

@ExperimentalCli
public fun main(args: Array<String>) {
    Policy.setPolicy(BasicPolicy())
    System.setSecurityManager(SecurityManager())

    val logger = Logger.getLogger("boot")

    val parser = ArgParser("boot", skipExtraArguments = true)

    val workingDir = System.getProperty("user.dir")

    val mavenCache by parser.option(ArgType.String, "maven-cache-location")
        .default("$workingDir${File.separator}cache${File.separator}maven")
    val softwareComponentCache by parser.option(ArgType.String, "software-component-cache-location")
        .default("$workingDir${File.separator}cache${File.separator}software-component")

    fun Subcommand.echo(value: String) = logger.log(Level.INFO, value)

    class CacheComponent : Subcommand(
        "cacheComponent",
        "Installs a single software component into the cache for later use."
    ) {
        val descriptor by option(ArgType.String, "descriptor").required()
        val location by option(ArgType.String, "location").required()
        val type by option(ArgType.String, "type").default(DEFAULT)
        val configuration by option(
            ArgType.String,
            "configuration",
            description = "Configures the given component. Should be in a map format where the separator is a ',' and key value pairs are assigned with '='. Example 'one=1,two=2,three=3'"
        ).default("")

        override fun execute() {
            echo("Setting up Software Component Graph.")
            val graph = initSoftwareComponentGraph(softwareComponentCache)

            echo("Setting up maven")
            initMaven(mavenCache, withBootDependencies { })

            echo("Creating a cacher")
            val cacher = graph.cacherOf(
                if (type == DEFAULT) SoftwareComponentRepositorySettings.default(location)
                else if (type == LOCAL) SoftwareComponentRepositorySettings.local(location)
                else throw IllegalArgumentException("Unknown Software Component repository type: '$type'. Only known types are '$DEFAULT' (for remote repositories) and '$LOCAL' (for local repositories)"),
            )

            val request = SoftwareComponentArtifactRequest(
                descriptor
            )

            echo("Caching")
            val cacheAttempt = cacher.cache(
                request
            )

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

                cacher.cacheConfiguration(
                    request.descriptor,
                    parsedConfig
                )
            }
        }
    }

    class LoadComponents : Subcommand(
        "loadComponents",
        "Loads any given number of components from the cache into the current runtime"
    ) {
        val components by argument(ArgType.String).vararg()

        override fun execute() {
            echo("Setting up Software Component Graph.")
            val graph = initSoftwareComponentGraph(softwareComponentCache)

            echo("Setting up maven")
            initMaven(mavenCache, withBootDependencies { })

            echo("Starting to load components '${components.joinToString()}'")

            components.map {
                SoftwareComponentArtifactRequest(it)
            }.forEach { req ->
                echo("Loading component: '$req'")

                val get = graph.get(req)

                if (get.isRight()) echo("Successfully loaded '$req' and all of its children.")
                else {
                    echo("Failed to load '$req'. This is a fatal exception.")
                    get.tapLeft { throw it }
                }
            }
        }
    }

    parser.subcommands(CacheComponent(), LoadComponents())

    parser.parse(args)
}
//
// TODO Create into api
//public fun main(args: Array<String>) {
//    Policy.setPolicy(BasicPolicy())
//    System.setSecurityManager(SecurityManager())
//
//    val parser = ArgParser("boot", skipExtraArguments = true)
//
//    val mavenCache by parser.option(ArgType.String, "maven-cache-location").required()
//    val pluginCache by parser.option(ArgType.String, "plugin-cache-location").required()
//    val pluginArguments by parser.option(ArgType.String, "plugins").required()
//
//    parser.parse(args)
//
////    Boot.maven = initMaven(mavenCache) { populateFrom ->
////        val mavenCentral = SimpleMaven.createContext(
////            SimpleMavenRepositorySettings.mavenCentral(
////                preferredHash = HashType.SHA1
////            )
////        )
////
////        val yakCentral = SimpleMaven.createContext(
////            SimpleMavenRepositorySettings.default(
////                "http://repo.yakclient.net/snapshots",
////                preferredHash = HashType.SHA1
////            )
////        )
////
////        val mavenLocal = SimpleMaven.createContext(
////            SimpleMavenRepositorySettings.local()
////        )
////
////        val allRepos = listOf(mavenCentral, yakCentral, mavenLocal);
////
////        { dependency: String ->
////            allRepos.find { it.populateFrom(dependency) }
////        }.also { implementation ->
////            implementation("org.jetbrains.kotlin:kotlin-stdlib:1.7.10")
////            implementation("io.arrow-kt:arrow-core:1.1.2")
////
////            implementation("com.durganmcbroom:event-api:1.0-SNAPSHOT")
////            implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.5")
////            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
////            implementation("net.yakclient:archives:1.0-SNAPSHOT")
////            implementation("com.durganmcbroom:artifact-resolver:1.0-SNAPSHOT")
////            implementation("com.durganmcbroom:artifact-resolver-simple-maven:1.0-SNAPSHOT")
////            implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.13.4")
////            implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.4")
////            implementation("net.yakclient:common-util:1.0-SNAPSHOT")
////        }
////    }
//
//
//
////    val appRef = readApp(appPath)
////    val (app, handle) = setupApp(appRef)
//
////    Boot.eventManager.accept(ApplicationLoadEvent(appRef, handle))
////
////    val instance = app.newInstance(args)
////
////    Boot.eventManager.accept(ApplicationLaunchEvent(handle, app))
////
////    instance.start(args)
//}

public fun enableAllComponents(
    node: SoftwareComponentNode,
    alreadyEnabled: Set<SoftwareComponentDescriptor> = HashSet(),
): Set<SoftwareComponentNode> {
    val enabledChildren = node.children.flatMapTo(HashSet(), ::enableAllComponents)

    val enabled = if (!alreadyEnabled.contains(node.descriptor)) node.component?.onEnable(ComponentLoadContext(node.configuration)) != null else false

    return if (enabled) enabledChildren + node else enabledChildren
}

public fun withBootDependencies(init: (MavenContext.(String) -> Boolean) -> Unit): (MavenContext.(String) -> Boolean) -> Unit =
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
        }

        init(populateFrom)
    }


//private fun parsePluginRequests(
//    plugins: String,
//): List<Pair<PluginArtifactRequest, PluginRepositorySettings>> {
//    val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
//    mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true)
//
//    data class CLIRepositorySettings(
//        val type: String,
//        val url: String,
//        val hashType: HashType = HashType.SHA1,
//        val releases: Boolean = true,
//        val snapshots: Boolean = true,
//    )
//
//    data class CLIArtifactRequest(
//        val descriptor: String,
//        val isTransitive: Boolean = true,
//        val includeScopes: Set<String> = setOf(),
//        val excludeArtifacts: Set<String> = setOf(),
//        val repository: CLIRepositorySettings,
//    )
//
//    val requests = mapper.readValue<List<CLIArtifactRequest>>(plugins)
//
//    return requests.map { req ->
//        val repository by req::repository
//
//        val request = PluginArtifactRequest(
//            req.descriptor,
//            req.isTransitive,
//            req.includeScopes,
//            req.excludeArtifacts
//        )
//
//        val layout =
//            if (repository.type == PluginRuntimeModelRepository.PLUGIN_REPO_TYPE) {
//                SimpleMavenDefaultLayout(
//                    repository.url,
//                    repository.hashType,
//                    repository.releases,
//                    repository.snapshots
//                )
//            } else if (repository.type == PluginRuntimeModelRepository.LOCAL_PLUGIN_REPO_TYPE) SimpleMavenRepositorySettings.local(
//                repository.url,
//                repository.hashType
//            ).layout else throw java.lang.IllegalArgumentException("Unknown plugin repository type: '${repository.type}'")
//
//
//        val settings = PluginRepositorySettings(
//            layout,
//            repository.hashType,
//        )
//
//        request to settings
//    }
//}

public fun initSoftwareComponentGraph(cache: String): SoftwareComponentGraph {
    val cachePath = Path.of(cache)

    return SoftwareComponentGraph(
        cachePath,
        CachingDataStore(SoftwareComponentDataAccess(cachePath)),
        BasicArchiveResolutionProvider(
            Archives.Finders.JPM_FINDER as ArchiveFinder<ArchiveReference>,
            Archives.Resolvers.JPM_RESOLVER
        ),
    )
}

public fun initMaven(
    cacheLocation: String,
    initDependencies: (MavenContext.(String) -> Boolean) -> Unit,
): MavenDependencyGraph {
    val dependencyGraph = createMavenDependencyGraph(cacheLocation, initDependencies)

    DependencyProviders.add(object :
        DependencyGraphProvider<SimpleMavenArtifactRequest, SimpleMavenRepositorySettings> {
        override val name: String = "simple-maven"
        override val graph: DependencyGraph<SimpleMavenArtifactRequest, *, SimpleMavenRepositorySettings> =
            dependencyGraph

        override fun parseRequest(request: Map<String, String>): SimpleMavenArtifactRequest? {
            val descriptorName = request["descriptor"] ?: return null
            val isTransitive = request["isTransitive"] ?: "true"
            val scopes = request["includeScopes"] ?: "compile,runtime,import"
            val excludeArtifacts = request["excludeArtifacts"] ?: ""

            return SimpleMavenArtifactRequest(
                SimpleMavenDescriptor.parseDescription(descriptorName) ?: return null,
                isTransitive.toBoolean(),
                scopes.split(',').toSet(),
                excludeArtifacts.split(',').toSet()
            )
        }

        override fun parseSettings(settings: Map<String, String>): SimpleMavenRepositorySettings? {
            val releasesEnabled = settings["releasesEnabled"] ?: "true"
            val snapshotsEnabled = settings["snapshotsEnabled"] ?: "true"
            val url = settings["url"] ?: return null
            val preferredHash = settings["preferredHash"] ?: "SHA1"

            val hashType = HashType.valueOf(preferredHash)

            return SimpleMavenRepositorySettings(
                SimpleMavenDefaultLayout(
                    url,
                    hashType,
                    releasesEnabled.toBoolean(),
                    snapshotsEnabled.toBoolean()
                ),
                hashType
            )
        }
    })

    return dependencyGraph
}

private typealias MavenContext = ResolutionContext<SimpleMavenArtifactRequest, ArtifactStub<SimpleMavenArtifactRequest, SimpleMavenRepositoryStub>, ArtifactReference<SimpleMavenArtifactMetadata, ArtifactStub<SimpleMavenArtifactRequest, SimpleMavenRepositoryStub>>>

private fun createMavenDependencyGraph(
    cachePath: String,
    initDependencies: (MavenContext.(String) -> Boolean) -> Unit,
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
    initDependencies: (MavenContext.(String) -> Boolean) -> Unit,
): MutableMap<ArchiveKey<SimpleMavenArtifactRequest>, DependencyNode> {
//    val initialGraph: MutableMap<ArchiveKey<SimpleMavenArtifactRequest>, DependencyNode> = HashMap()

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
                return delegate[UnVersionedArchiveKey(key.request)]
            }
        }

    fun MavenContext.populate(
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

    fun MavenContext.populateFrom(
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

//private const val APP_ENTRY_RESOURCE_LOCATION = "META-INF/app.json"
//
//private data class BootAppProperties(
//    val name: String,
//    val appClassName: String,
//    val dependencies: List<BootAppDependency>,
//)
//
//private data class BootAppDependency(
//    val type: String,
//    val request: Map<String, String>,
//    val repository: Map<String, String>,
//)
//
//private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
//
//private fun parseAppProperties(properties: InputStream): BootAppProperties {
//    return mapper.readValue(properties)
//}
//
//private fun readApp(app: String): ArchiveReference {
//    val path = Path.of(app)
//
//    check(Files.exists(path)) { "Given argument 'app-entry' (value: '$app') cannot be found in the file system!" }
//
//    return Archives.find(path, Archives.Finders.JPM_FINDER)
//}
//
//private fun setupApp(ref: ArchiveReference): Pair<BootApplication, ArchiveHandle> {
//    val appPath = ref.location.path
//
//    val properties = ref.reader[APP_ENTRY_RESOURCE_LOCATION]
//        ?.let { parseAppProperties(it.resource.open()) }
//        ?: throw IllegalStateException("Application Entry Point: '$appPath' should have a property file named: $'$APP_ENTRY_RESOURCE_LOCATION'")
//
//    val dependencies = properties.dependencies.map {
//        val provider: DependencyGraphProvider<*, *>? = DependencyProviders.getByType(it.type)
//
//        provider?.getArtifact(it.repository, it.request)?.orNull()
//            ?: throw IllegalArgumentException("Failed to load artifact '${it.request}' of type '${it.type}' from repository '${it.repository}'")
//    }
//
//    fun handleOrChildren(node: DependencyNode): Set<ArchiveHandle> = node.handleOrChildren()
//
//    val children = dependencies.flatMapTo(HashSet(), ::handleOrChildren) + ModuleLayer.boot().modules()
//        .map(JpmArchives::moduleToArchive)
//
//    val handle = Archives.resolve(
//        ref,
//        IntegratedLoader(
//            sp = ArchiveSourceProvider(ref),
//            cp = DelegatingClassProvider(
//                children
//                    .map(::ArchiveClassProvider)
//            ),
//            parent = ClassLoader.getSystemClassLoader()
//        ),
//        Archives.Resolvers.JPM_RESOLVER,
//        children
//    ).archive
//
//    val tryLoadClass = runCatching { handle.classloader.loadClass(properties.appClassName) }
//
//    val entrypointClass = tryLoadClass.getOrNull()
//        ?: throw IllegalStateException("Failed to load class '${properties.appClassName}' from Entrypoint jar: '$appPath'")
//    val entrypointConstructor = runCatching { entrypointClass.getConstructor() }.getOrNull()
//        ?: throw IllegalStateException("ApplicationEntrypoint class: '${properties.appClassName}' must have a no-arg constructor!")
//    val entrypoint = runCatching { entrypointConstructor.newInstance() }.let {
//        it.getOrNull()
//            ?: throw IllegalStateException("Failed to instantiate type: '${properties.appClassName}' during entrypoint construction. Error was: '${it.exceptionOrNull()?.message}'")
//    }
//
//    return (entrypoint as? BootApplication
//        ?: throw IllegalStateException("Type given as application entrypoint is not a child of '${BootApplication::class.java.name}'.")) to handle
//}

