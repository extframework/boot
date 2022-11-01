package net.yakclient.boot.test.dependency

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.artifact.resolver.simple.maven.*
import net.yakclient.archives.ArchiveFinder
import net.yakclient.archives.ArchiveReference
import net.yakclient.archives.Archives
import net.yakclient.archives.JpmArchives
import net.yakclient.boot.archive.ArchiveKey
import net.yakclient.boot.archive.BasicArchiveResolutionProvider
import net.yakclient.boot.archive.moduleNameFor
import net.yakclient.boot.dependency.DependencyNode
import net.yakclient.boot.maven.MavenDataAccess
import net.yakclient.boot.maven.MavenDependencyGraph
import net.yakclient.boot.store.CachingDataStore
import net.yakclient.boot.toSafeResource
import net.yakclient.common.util.resolve
import java.nio.file.Path
import kotlin.test.Test

private typealias MavenContext = ResolutionContext<SimpleMavenArtifactRequest, ArtifactStub<SimpleMavenArtifactRequest, SimpleMavenRepositoryStub>, ArtifactReference<SimpleMavenArtifactMetadata, ArtifactStub<SimpleMavenArtifactRequest, SimpleMavenRepositoryStub>>>

class TestDependencyGraph {


    @Test
    fun `Test maven basic dependency loading`() {
        val graph = createMavenDependencyGraph()

        val loader = graph.cacherOf(
            SimpleMavenRepositorySettings.mavenCentral(
                preferredHash = HashType.MD5
            )
        )

        val node = loader.cache(
            SimpleMavenArtifactRequest(
                "org.springframework:spring-core:5.3.22",
               includeScopes = setOf("compile", "runtime", "import")
            )
        )
        println(node)


//        node.prettyPrint { handle, d ->
//            println((0 until d).joinToString(separator = "", postfix = " -> ${handle?.name ?: "%POM%"}") { "    " })
//        }

//        class VersionIndependentMavenKey(
//            desc: SimpleMavenDescriptor
//        ) : VersionIndependentDependencyKey {
//            val group by desc::group
//            val artifact by desc::artifact
//            val classifier by desc::classifier
//            override fun equals(other: Any?): Boolean {
//                if (this === other) return true
//                if (other !is VersionIndependentMavenKey) return false
//
//                if (group != other.group) return false
//                if (artifact != other.artifact) return false
//                if (classifier != other.classifier) return false
//
//                return true
//            }
//
//            override fun hashCode(): Int {
//                var result = group.hashCode()
//                result = 31 * result + artifact.hashCode()
//                result = 31 * result + (classifier?.hashCode() ?: 0)
//                return result
//            }
//        }
//
//        val mavenMetadataProvider = object : DependencyMetadataProvider<SimpleMavenDescriptor> {
//            override val descriptorType: KClass<SimpleMavenDescriptor> = SimpleMavenDescriptor::class
//            override fun keyFor(desc: SimpleMavenDescriptor): VersionIndependentDependencyKey =
//                VersionIndependentMavenKey(desc)
//
//            override fun stringToDesc(d: String): SimpleMavenDescriptor? = SimpleMavenDescriptor.parseDescription(d)
//
//            override fun relativePath(d: SimpleMavenDescriptor): Path =
//                Path.of("", *d.group.split(".").toTypedArray()) resolve d.artifact resolve d.version
//
//            override fun descToString(d: SimpleMavenDescriptor): String = d.toString()
//
//            override fun jarName(d: SimpleMavenDescriptor): String = "${d.artifact}-${d.version}.jar"
//        }

//        val artifactGraph = ArtifactGraph(ResolutionGroup) {
//            graphOf(SimpleMaven).register()
//        }
//        val graph = DependencyGraph(
//            Path.of(System.getProperty("user.dir")) resolve "cache/lib",
//            listOf(mavenMetadataProvider),
//            artifactGraph,
//            Archives.Finders.JPM_FINDER,
//            Archives.Resolvers.JPM_RESOLVER,
//        )
//
//        val node = graph.createLoader(SimpleMaven) {
//            useBasicRepoReferencer()
//            useMavenCentral()
//        }.load("org.springframework.boot:spring-boot:2.7.2") ?: throw Exception("Didnt load the dependency!")
//
//        node
    }

    companion object {
        fun createMavenDependencyGraph() : MavenDependencyGraph {
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
            val moduleAwareGraph = object : MutableMap<ArchiveKey<SimpleMavenArtifactRequest>, DependencyNode> by delegate {
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

            fun MavenContext.populateFrom(
                desc: String,
            ) {
                populateFrom(
                    SimpleMavenArtifactRequest(
                        desc,
                        includeScopes = setOf("compile", "runtime", "import")
                    )
                )
            }


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


            val basePath = Path.of(System.getProperty("user.dir")) resolve "cache/lib"
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
    }
}