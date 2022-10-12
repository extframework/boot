package net.yakclient.boot

import arrow.core.identity
import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.artifact.resolver.simple.maven.*
import com.durganmcbroom.artifact.resolver.simple.maven.layout.SimpleMavenDefaultLayout
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.cli.*
import net.yakclient.archives.*
import net.yakclient.boot.archive.ArchiveKey
import net.yakclient.boot.archive.BasicArchiveResolutionProvider
import net.yakclient.boot.archive.moduleNameFor
import net.yakclient.boot.dependency.*
import net.yakclient.boot.event.ApplicationLaunchEvent
import net.yakclient.boot.event.ApplicationLoadEvent
import net.yakclient.boot.event.EventPipelineManager
import net.yakclient.boot.event.PluginLoadEvent
import net.yakclient.boot.loader.ArchiveClassProvider
import net.yakclient.boot.loader.ArchiveSourceProvider
import net.yakclient.boot.loader.DelegatingClassProvider
import net.yakclient.boot.loader.IntegratedLoader
import net.yakclient.boot.maven.MavenDataAccess
import net.yakclient.boot.maven.MavenDependencyGraph
import net.yakclient.boot.plugin.*
import net.yakclient.boot.plugin.artifact.PluginArtifactRequest
import net.yakclient.boot.plugin.artifact.PluginRepositorySettings
import net.yakclient.boot.store.CachingDataStore
import net.yakclient.common.util.immutableLateInit
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.Policy
import java.util.*

public object Boot {
    public var maven: MavenDependencyGraph by immutableLateInit()
    public val eventManager: EventPipelineManager = EventPipelineManager()
}

public fun main(args: Array<String>) {
    Policy.setPolicy(BasicPolicy())
    System.setSecurityManager(SecurityManager())

    val parser = ArgParser("boot", skipExtraArguments = true)

    val appPath by parser.option(ArgType.String, "app").required()
    val mavenCache by parser.option(ArgType.String, "maven-cache-location").required()
    val pluginCache by parser.option(ArgType.String, "plugin-cache-location").required()
    val pluginArguments by parser.option(ArgType.String, "plugins").required()

    parser.parse(args)

    println(appPath)
    println(mavenCache)
    println(pluginCache)
    println(pluginArguments)

    Boot.maven = createMaven(mavenCache) { populateFrom ->
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

        mavenCentral.populateFrom("io.arrow-kt:arrow-core:1.1.2")
        mavenCentral.populateFrom("org.jetbrains.kotlinx:kotlinx-cli:0.3.5")
        mavenCentral.populateFrom("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")

        mavenLocal.populateFrom("com.durganmcbroom:artifact-resolver:1.0-SNAPSHOT")
        mavenLocal.populateFrom("com.durganmcbroom:artifact-resolver-simple-maven:1.0-SNAPSHOT")

        mavenCentral.populateFrom("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.13.4")
        mavenCentral.populateFrom("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.4")

        yakCentral.populateFrom("net.yakclient:common-util:1.0-SNAPSHOT")
        yakCentral.populateFrom("net.yakclient:archives:1.0-SNAPSHOT")
    }

    val pluginRequests = parsePluginRequests(pluginArguments)

    val pluginGraph = initPluginSystem(pluginCache)

    pluginRequests.forEach { (req, settings) ->
        pluginGraph.loaderOf(settings).load(req).fold({ throw it }, ::identity)
    }

    pluginGraph.graph.values
        .onEach { it.plugin?.onLoad() }
        .onEach {
            if (it.plugin != null) Boot.eventManager.accept(PluginLoadEvent(it, it.plugin))
        }

    val appRef = readApp(appPath)

    Boot.eventManager.accept(ApplicationLoadEvent(appRef))

    val (app, handle) = setupApp(appRef)
    val instance = app.newInstance(args)

    Boot.eventManager.accept(ApplicationLaunchEvent(handle, app))

    instance.start(args)
}

private fun parsePluginRequests(
    plugins: String,
): List<Pair<PluginArtifactRequest, PluginRepositorySettings>> {
    val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true)

    data class CLIRepositorySettings(
        val type: String,
        val url: String,
        val hashType: HashType = HashType.SHA1,
        val releases: Boolean = true,
        val snapshots: Boolean = true,
    )

    data class CLIArtifactRequest(
        val descriptor: String,
        val isTransitive: Boolean = true,
        val includeScopes: Set<String> = setOf(),
        val excludeArtifacts: Set<String> = setOf(),
        val repository: CLIRepositorySettings,
    )

    val requests = mapper.readValue<List<CLIArtifactRequest>>(plugins)

    return requests.map { req ->
        val repository by req::repository

        val request = PluginArtifactRequest(
            req.descriptor,
            req.isTransitive,
            req.includeScopes,
            req.excludeArtifacts
        )

        val layout =
            if (repository.type == PluginRuntimeModelRepository.PLUGIN_REPO_TYPE) {
                SimpleMavenDefaultLayout(
                    repository.url,
                    repository.hashType,
                    repository.releases,
                    repository.snapshots
                )
            } else if (repository.type == PluginRuntimeModelRepository.LOCAL_PLUGIN_REPO_TYPE) SimpleMavenRepositorySettings.local(
                repository.url,
                repository.hashType
            ).layout else throw java.lang.IllegalArgumentException("Unknown plugin repository type: '${repository.type}'")


        val settings = PluginRepositorySettings(
            layout,
            repository.hashType,
        )

        request to settings
    }
}

private fun initPluginSystem(pluginCache: String): PluginGraph {
    val cachePath = Path.of(pluginCache)

    return PluginGraph(
        cachePath,
        CachingDataStore(PluginDataAccess(cachePath)),
        BasicArchiveResolutionProvider(
            Archives.Finders.JPM_FINDER as ArchiveFinder<ArchiveReference>,
            Archives.Resolvers.JPM_RESOLVER
        ),
    )
}

public fun createMaven(
    cacheLocation: String,
    initDependencies: (MavenContext.(String) -> Unit) -> Unit,
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
    initDependencies: (MavenContext.(String) -> Unit) -> Unit,
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
    initDependencies: (MavenContext.(String) -> Unit) -> Unit,
): MutableMap<ArchiveKey<SimpleMavenArtifactRequest>, DependencyNode> {
    val initialGraph: MutableMap<ArchiveKey<SimpleMavenArtifactRequest>, DependencyNode> = HashMap()

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
                return initialGraph[UnVersionedArchiveKey(key.request)] ?: delegate[key]
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

        initialGraph[UnVersionedArchiveKey(request)] = node

        return node
    }

    fun MavenContext.populateFrom(
        request: SimpleMavenArtifactRequest,
    ) {
        val repo = repositoryContext.artifactRepository
        val ref = repo.get(request).orNull()
            ?: throw IllegalArgumentException("Unable to find artifact from request '$request'.")

        populate(ref, request)
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

private const val APP_ENTRY_RESOURCE_LOCATION = "META-INF/app.json"

private data class BootAppProperties(
    val name: String,
    val appClassName: String,
    val dependencies: List<BootAppDependency>,
)

private data class BootAppDependency(
    val type: String,
    val request: Map<String, String>,
    val repository: Map<String, String>,
)

private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

private fun parseAppProperties(properties: InputStream): BootAppProperties {
    return mapper.readValue(properties)
}

private fun readApp(app: String): ArchiveReference {
    val path = Path.of(app)

    check(Files.exists(path)) { "Given argument 'app-entry' (value: '$app') cannot be found in the file system!" }

    return Archives.find(path, Archives.Finders.JPM_FINDER)
}

private fun setupApp(ref: ArchiveReference): Pair<BootApplication, ArchiveHandle> {
    val appPath = ref.location.path

    val properties = ref.reader[APP_ENTRY_RESOURCE_LOCATION]
        ?.let { parseAppProperties(it.resource.open()) }
        ?: throw IllegalStateException("Application Entry Point: '$appPath' should have a property file named: $'$APP_ENTRY_RESOURCE_LOCATION'")

    val dependencies = properties.dependencies.map {
        val provider: DependencyGraphProvider<*, *>? = DependencyProviders.getByType(it.type)

        provider?.getArtifact(it.request, it.repository)?.orNull()
            ?: throw IllegalArgumentException("Failed to load artifact '${it.request}' of type '${it.type}' from repository '${it.repository}'")
    }

    fun handleOrChildren(node: DependencyNode): List<ArchiveHandle> {
        return node.archive?.let(::listOf) ?: node.children.flatMap(::handleOrChildren)
    }

    val children = dependencies.flatMapTo(HashSet(), ::handleOrChildren) + ModuleLayer.boot().modules()
        .map(JpmArchives::moduleToArchive)

    val handle = Archives.resolve(
        ref,
        IntegratedLoader(
            sp = ArchiveSourceProvider(ref),
            cp = DelegatingClassProvider(
                children
                    .map(::ArchiveClassProvider)
            ),
            parent = ClassLoader.getSystemClassLoader()
        ),
        Archives.Resolvers.JPM_RESOLVER,
        children
    ).archive

    val tryLoadClass = runCatching { handle.classloader.loadClass(properties.appClassName) }

    val entrypointClass = tryLoadClass.getOrNull()
        ?: throw IllegalStateException("Failed to load class '${properties.appClassName}' from Entrypoint jar: '$appPath'")
    val entrypointConstructor = runCatching { entrypointClass.getConstructor() }.getOrNull()
        ?: throw IllegalStateException("ApplicationEntrypoint class: '${properties.appClassName}' must have a no-arg constructor!")
    val entrypoint = runCatching { entrypointConstructor.newInstance() }.getOrNull()
        ?: throw IllegalStateException("Failed to instantiate type: '${properties.appClassName}' during entrypoint construction.")

    return (entrypoint as? BootApplication
        ?: throw IllegalStateException("Type given as application entrypoint is not a child of '${BootApplication::class.java.name}'.")) to handle
}

