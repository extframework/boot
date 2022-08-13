package net.yakclient.boot.test.dependency

import com.durganmcbroom.artifact.resolver.ArtifactGraph
import com.durganmcbroom.artifact.resolver.group.ResolutionGroup
import com.durganmcbroom.artifact.resolver.group.graphOf
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMaven
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import net.yakclient.archives.Archives
import net.yakclient.boot.dependency.DependencyMetadataProvider
import net.yakclient.boot.dependency.DependencyGraph
import net.yakclient.boot.dependency.VersionIndependentDependencyKey
import net.yakclient.common.util.resolve
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlin.test.Test

class TestDependencyGraph {
    @Test
    fun `Test basic dependency loading`() {
        println(System.getProperty("user.dir"))

        class VersionIndependentMavenKey(
            desc: SimpleMavenDescriptor
        ) : VersionIndependentDependencyKey {
            val group by desc::group
            val artifact by desc::artifact
            val classifier by desc::classifier
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is VersionIndependentMavenKey) return false

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

        val mavenMetadataProvider = object : DependencyMetadataProvider<SimpleMavenDescriptor> {
            override val descriptorType: KClass<SimpleMavenDescriptor> = SimpleMavenDescriptor::class
            override fun keyFor(desc: SimpleMavenDescriptor): VersionIndependentDependencyKey =
                VersionIndependentMavenKey(desc)

            override fun stringToDesc(d: String): SimpleMavenDescriptor? = SimpleMavenDescriptor.parseDescription(d)

            override fun relativePath(d: SimpleMavenDescriptor): Path =
                Path.of("", *d.group.split(".").toTypedArray()) resolve d.artifact resolve d.version

            override fun descToString(d: SimpleMavenDescriptor): String = d.toString()

            override fun jarName(d: SimpleMavenDescriptor): String = "${d.artifact}-${d.version}.jar"
        }

        val artifactGraph = ArtifactGraph(ResolutionGroup) {
            graphOf(SimpleMaven).register()
        }
        val graph = DependencyGraph(
            Path.of(System.getProperty("user.dir")) resolve "cache/lib",
            listOf(mavenMetadataProvider),
            artifactGraph,
            Archives.Finders.JPM_FINDER,
            Archives.Resolvers.JPM_RESOLVER,
        )

        val node = graph.createLoader(SimpleMaven) {
            useBasicRepoReferencer()
            useMavenCentral()
        }.load("org.springframework.boot:spring-boot:2.7.2") ?: throw Exception("Didnt load the dependency!")

        node.prettyPrint { handle, d ->
            println((0 until d).joinToString(separator = "", postfix = " -> ${handle?.name ?: "%POM%"}") { "    " })
        }
    }
}