package net.yakclient.boot

import com.durganmcbroom.artifact.resolver.simple.maven.*
import kotlinx.cli.*
import net.yakclient.archives.*
import net.yakclient.boot.archive.ArchiveLoadException
import net.yakclient.boot.component.*
import net.yakclient.boot.component.artifact.SoftwareComponentArtifactRequest
import net.yakclient.boot.component.artifact.SoftwareComponentDescriptor
import net.yakclient.boot.component.artifact.SoftwareComponentRepositorySettings
import net.yakclient.boot.dependency.DependencyTypeProvider
import java.nio.file.Path

public interface BootInstance {
    public val location: Path
    public val dependencyTypes: DependencyTypeProvider
    public val componentGraph: SoftwareComponentGraph

    public fun isCached(descriptor: SoftwareComponentDescriptor) : Boolean

    public fun cache(request: SoftwareComponentArtifactRequest, location: SoftwareComponentRepositorySettings)

    @Throws(ArchiveLoadException::class)
    public fun <T : ComponentConfiguration, I : ComponentInstance<T>> new(
            descriptor: SoftwareComponentDescriptor,
            factoryType: Class<out ComponentFactory<T, I>>,
            configuration: T
    ): I
}

public inline fun <T : ComponentConfiguration, I : ComponentInstance<T>, reified F : ComponentFactory<T, I>> BootInstance.new(descriptor: SoftwareComponentDescriptor, configuration: T): I =
        this.new(descriptor, F::class.java, configuration)






//
//public data class BootInstance(
//    val location: Path,
//    val dependencyProviders: DependencyTypeProvider,
//) {
//    val componentGraph: SoftwareComponentGraph = initSoftwareComponentGraph(location resolve "cmpts", dependencyProviders, this)
//
//    init {
//        initMaven(
//                dependencyProviders,
//                location resolve "m2"
//        )
//    }
//
//    public fun isCached(
//            request: SoftwareComponentArtifactRequest
//    ): Boolean {
//        return componentGraph.isCached(request)
//    }
//
//    @Throws(ArchiveLoadException::class)
//    public fun cache(
//            request: SoftwareComponentArtifactRequest,
//            location: SoftwareComponentRepositorySettings,
//    ): Boolean {
//        val cacher = componentGraph.cacherOf(
//                location,
//        )
//
//        val cacheAttempt = cacher.cache(
//                request
//        )
//
//        return cacheAttempt.tapLeft { throw it }.isRight()
//    }
//
//    @Throws(ArchiveLoadException::class)
//    public fun <T : ComponentConfiguration, I : ComponentInstance<T>> new(
//            request: SoftwareComponentArtifactRequest,
//            factoryType: Class<out ComponentFactory<T, I>>,
//            configuration: T
//    ): I {
//        val it = componentGraph.get(request).tapLeft { throw it }.orNull()!!
//
//        check(factoryType.isInstance(it.factory))
//
//        val factory = it.factory as? ComponentFactory<T, I>
//                ?: throw IllegalArgumentException("Cannot start a Component with no factory, you must start its children instead. Use the Software component graph to do this.")
//
//        return factory.new(configuration)
//    }
//
//    @Throws(ArchiveLoadException::class)
//    public fun <T : ComponentConfiguration, I : ComponentInstance<T>> new(
//            descriptor: SoftwareComponentDescriptor,
//            factoryType: Class<out ComponentFactory<T, I>>,
//            configuration: T
//    ): I {
//        return new(SoftwareComponentArtifactRequest(descriptor), factoryType, configuration)
//    }
//
//    public fun <T: ComponentFactory<*, *>> factoryOf(
//            descriptor: SoftwareComponentDescriptor,
//            factoryType: Class<T>,
//    ) : T? {
//        return factoryOf(SoftwareComponentArtifactRequest(descriptor), factoryType)
//    }
//
//    public fun <T: ComponentFactory<*, *>> factoryOf(
//            request: SoftwareComponentArtifactRequest,
//            factoryType: Class<T>,
//    ) : T? {
//        val factory = componentGraph.get(request).orNull()?.factory ?: return null
//        check(factoryType.isInstance(factory))
//        return factory as T
//    }
//
//    public companion object {
//        public fun new(base: String): BootInstance = BootInstance(
//                Path.of(base),
//                DependencyTypeProvider())
//
//        public inline fun <T : ComponentConfiguration, I : ComponentInstance<T>, reified F : ComponentFactory<T, I>> BootInstance.new(descriptor: SoftwareComponentDescriptor, configuration: T): I =
//                this.new(descriptor, F::class.java, configuration)
//
//        public inline fun <reified T: ComponentFactory<*, *>> BootInstance.factoryOf(descriptor: SoftwareComponentDescriptor) : T? {
//            return factoryOf(descriptor, T::class.java)
//        }
//    }
//}

//@ExperimentalCli
//public fun main(args: Array<String>) {
//    // Setup logger
//    val logger = Logger.getLogger("boot")
//
//    // Setup argument parser
//    val parser = ArgParser("boot")
//
//    // Get working dir
//
//    val workingDir by parser.option(ArgType.String, "working-dir", "w").required()
//    // Parse args
//
//    // Create Boot context for later use
//    val boot by lazy { BootInstance.new(workingDir) }
//
//    fun echo(value: String) = logger.log(Level.INFO, value)
//
//    // Start of cli commands
//    class CacheComponent : Subcommand(
//            "cache",
//            "Installs a single software component into the cache for later use."
//    ) {
//        val descriptor by option(ArgType.String, "descriptor").required()
//        val location by option(ArgType.String, "location").required()
//        val type by option(ArgType.String, "type").default(DEFAULT)
//
//        override fun execute() {
//            echo("Setting up maven")
//
//            val settings = when (type) {
//                DEFAULT -> SoftwareComponentRepositorySettings.default(
//                        location,
//                        preferredHash = HashType.SHA1
//                )
//
//                LOCAL -> SoftwareComponentRepositorySettings.local(
//                        location,
//                        preferredHash = HashType.SHA1
//                )
//
//                else -> throw IllegalArgumentException("Unknown Software Component repository type: '$type'. Only known types are '$DEFAULT' (for remote repositories) and '$LOCAL' (for local repositories)")
//            }
//
//            val request = SoftwareComponentArtifactRequest(
//                    descriptor,
//            )
//
//            try {
//                boot.cache(request, settings)
//                echo("Successfully cached the component: '$descriptor'!")
//            } catch (ex: ArchiveLoadException) {
//                echo("Failed to cache component, an error occurred. Throwing.")
//                throw ex
//            }
//        }
//    }
//
//    class StartComponents : Subcommand(
//            "start",
//            "Starts any given number of components and their children."
//    ) {
//        val component by argument(ArgType.String)
//        val configPath by option(ArgType.String, "configuration", "d")
//
//        override fun execute() {
//            val node = boot.componentGraph.get(
//                    SoftwareComponentArtifactRequest(checkNotNull(SoftwareComponentDescriptor.parseDescription(component)) { "Invalid component descriptor: '$component'" })
//            ).tapLeft { throw it }.orNull()!!
//
//            echo("Parsing configuration: '$configPath'")
//            val factory = node.factory as? ComponentFactory<ComponentConfiguration, ComponentInstance<ComponentConfiguration>>
//                    ?: throw IllegalArgumentException("Cannot start component: '$component' because it does not have a factory, is either a library or only a transitive component.")
//
//            val configTree = configPath?.let {
//                ContextNodeValueImpl(ObjectMapper().readValue<Any>(File(it)))
//            } ?: ContextNodeValueImpl(Unit)
//
//            val realConfig = node.factory.parseConfiguration(configTree)
//
//            val instance = factory.new(realConfig)
//
//            echo("Starting Instance")
//            instance.start()
//        }
//    }
//
//    parser.subcommands(CacheComponent(), StartComponents())
//
//    parser.parse(args)
//}
////public fun enableAllComponents(
////        node: SoftwareComponentNode,
////        boot: BootInstance,
////        alreadyEnabled: Set<SoftwareComponentDescriptor> = HashSet(),
////): Set<SoftwareComponentNode> {
////    val enabledChildren = node.children.flatMapTo(HashSet()) { enableAllComponents(it, boot, alreadyEnabled) }
////
////    val enabled = if (!alreadyEnabled.contains(node.descriptor)) node.component?.onEnable(
////            ComponentContext(
////                    node.configuration,
////                    boot
////            )
////    ) != null else false
////
////    return if (enabled) enabledChildren + node else enabledChildren
////}
//// Convenience methods
//private fun initMaven(
//        dependencyProviders: DependencyTypeProvider,
//        cache: Path
//) = dependencyProviders.add(
//        createMavenProvider(cache)
//)
//
//
//
//
//private fun initSoftwareComponentGraph(
//        cache: Path,
//        dependencyProviders: DependencyTypeProvider,
//        boot: BootInstance
//): SoftwareComponentGraph {
//    return SoftwareComponentGraph(
//            cache,
//            CachingDataStore(SoftwareComponentDataAccess(cache)),
//            BasicArchiveResolutionProvider(
//                    Archives.Finders.ZIP_FINDER as ArchiveFinder<ArchiveReference>,
//                    Archives.Resolvers.ZIP_RESOLVER
//            ),
//            dependencyProviders,
//            boot,
//    )
//}
//
//public fun createMavenProvider(
//        cache: Path,
//): DependencyGraphProvider<*, *, *> {
//    val dependencyGraph = createMavenDependencyGraph(cache)
//
//    return object : DependencyGraphProvider<SimpleMavenDescriptor, SimpleMavenArtifactRequest, SimpleMavenRepositorySettings> {
//        override val name: String = "simple-maven"
//        override val graph: DependencyGraph<SimpleMavenDescriptor, SimpleMavenRepositorySettings> =
//                dependencyGraph
//
//        override fun parseRequest(request: Map<String, String>): SimpleMavenArtifactRequest? {
//            val descriptorName = request["descriptor"] ?: return null
//            val isTransitive = request["isTransitive"] ?: "true"
//            val scopes = request["includeScopes"] ?: "compile,runtime,import"
//            val excludeArtifacts = request["excludeArtifacts"]
//
//            return SimpleMavenArtifactRequest(
//                    SimpleMavenDescriptor.parseDescription(descriptorName) ?: return null,
//                    isTransitive.toBoolean(),
//                    scopes.split(',').toSet(),
//                    excludeArtifacts?.split(',')?.toSet() ?: setOf()
//            )
//        }
//
//        override fun parseSettings(settings: Map<String, String>): SimpleMavenRepositorySettings? {
//            val releasesEnabled = settings["releasesEnabled"] ?: "true"
//            val snapshotsEnabled = settings["snapshotsEnabled"] ?: "true"
//            val location = settings["location"] ?: return null
//            val preferredHash = settings["preferredHash"] ?: "SHA1"
//            val type = settings["type"] ?: "default"
//
//            val hashType = HashType.valueOf(preferredHash)
//
//            return when (type) {
//                "default" -> SimpleMavenRepositorySettings.default(
//                        location,
//                        releasesEnabled.toBoolean(),
//                        snapshotsEnabled.toBoolean(),
//                        hashType
//                )
//
//                "local" -> SimpleMavenRepositorySettings.local(location, hashType)
//                else -> return null
//            }
//        }
//    }
//}
//
////public typealias MavenPopulateContext = ResolutionContext<SimpleMavenArtifactRequest, ArtifactStub<SimpleMavenArtifactRequest, SimpleMavenRepositoryStub>, ArtifactReference<SimpleMavenArtifactMetadata, ArtifactStub<SimpleMavenArtifactRequest, SimpleMavenRepositoryStub>>>
//
////private data class BasicResolutionResult(override val archive: ArchiveHandle) : ResolutionResult
//
////private const val DASH_DOT_PATTERN = "-(\\d+(\\.|$))"
//
//internal fun createMavenDependencyGraph(
//        cachePath: Path,
//): MavenDependencyGraph {
//    val resolutionProvider = object : BasicArchiveResolutionProvider<ArchiveReference, ZipResolutionResult>(
//            Archives.Finders.ZIP_FINDER as ArchiveFinder<ArchiveReference>,
//            Archives.Resolvers.ZIP_RESOLVER,
//    ) {}
//    val graph = MavenDependencyGraph(
//            cachePath,
//            CachingDataStore(
//                    MavenDataAccess(cachePath)
//            ),
//            resolutionProvider
////         HashMap<ArchiveKey<SimpleMavenArtifactRequest>, DependencyNode>(),
//    )
////        object : BasicArchiveResolutionProvider<ArchiveReference, BasicResolutionResult>(
////            Archives.Finders.ZIP_FINDER as ArchiveFinder<ArchiveReference>,
////            object : ArchiveResolver<ArchiveReference, BasicResolutionResult> {
////                private val delegate = Archives.Resolvers.ZIP_RESOLVER
////                override val type: KClass<ArchiveReference> by delegate::type
////
////                override fun resolve(
////                    archiveRefs: List<ArchiveReference>,
////                    clProvider: ClassLoaderProvider<ArchiveReference>,
////                    parents: Set<ArchiveHandle>
////                ): List<BasicResolutionResult> = delegate.resolve(archiveRefs, clProvider, parents).map {
////                    BasicResolutionResult(it.archive)
////                }
////            }
////        ) {
////            override fun resolve(
////                resource: Path,
////                classLoader: ClassLoaderProvider<ArchiveReference>,
////                parents: Set<ArchiveHandle>
////            ): Either<ArchiveLoadException, BasicResolutionResult> {
////                val fileName = resource.fileName.toString()
////                val artifactName =
////                    fileName.substring(0, Regex(DASH_DOT_PATTERN).find(fileName)?.range?.first ?: (fileName.length))
////
////                val name = moduleNameFor(LocalResource(resource.toUri()), artifactName)
////
////                val maybeModule = ModuleLayer.boot().findModule(name).orElseGet { null }
////
////                return maybeModule
////                    ?.let(JpmArchives::moduleToArchive)
////                    ?.let(::BasicResolutionResult)
////                    ?.right() ?: super.resolve(resource, classLoader, parents)
////            }
////        },
//
//    return graph
//}

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