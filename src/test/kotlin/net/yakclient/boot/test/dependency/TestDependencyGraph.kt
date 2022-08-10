package net.yakclient.boot.test.dependency

import com.durganmcbroom.artifact.resolver.ArtifactGraph
import com.durganmcbroom.artifact.resolver.group.ResolutionGroup
import com.durganmcbroom.artifact.resolver.group.graphOf
import com.durganmcbroom.artifact.resolver.simple.maven.HashType
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMaven
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.layout.SnapshotSimpleMavenLayout
import net.yakclient.archives.Archives
import net.yakclient.boot.dependency.DependencyMetadataProvider
import net.yakclient.boot.dependency.DependencyGraph
import net.yakclient.boot.dependency.DependencyStore
import net.yakclient.boot.load
import net.yakclient.common.util.resolve
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.reflect.KClass

class TestDependencyGraph {
    @Test
    fun `Test basic dependency loading`() {
        println(System.getProperty("user.dir"))
        val mavenMetadataProvider = object : DependencyMetadataProvider<SimpleMavenDescriptor> {
            override val descriptorType: KClass<SimpleMavenDescriptor> = SimpleMavenDescriptor::class

            override fun stringToDesc(d: String): SimpleMavenDescriptor? = SimpleMavenDescriptor.parseDescription(d)

            override fun relativePath(d: SimpleMavenDescriptor): Path =
                Path.of("", *d.group.split(".").toTypedArray()) resolve d.artifact resolve d.version

            override fun descToString(d: SimpleMavenDescriptor): String = d.toString()

            override fun jarName(d: SimpleMavenDescriptor): String = "${d.artifact}-${d.version}.jar"
        }

        val store = DependencyStore(
            Path.of(System.getProperty("user.dir")) resolve "cache/lib",
            listOf(mavenMetadataProvider)
        )
        val artifactGraph = ArtifactGraph(ResolutionGroup) {
            graphOf(SimpleMaven).register()
        }
        val graph = DependencyGraph(
            store,
            artifactGraph,
            Archives.Finders.JPM_FINDER,
            Archives.Resolvers.JPM_RESOLVER,
        )

        val node = graph.createLoader(SimpleMaven) {
            useBasicRepoReferencer()
            layout = SnapshotSimpleMavenLayout("http://repo.yakclient.net/snapshots", HashType.SHA1)
        }.load("net.yakclient:common-util:1.0-SNAPSHOT") ?: throw Exception("Didnt load the dependency!")

        println(node)
    }
}