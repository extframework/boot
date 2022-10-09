package net.yakclient.boot.test.plugin

import com.durganmcbroom.artifact.resolver.simple.maven.HashType
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.artifact.resolver.simple.maven.layout.MAVEN_CENTRAL_REPO
import com.durganmcbroom.artifact.resolver.simple.maven.layout.SimpleMavenDefaultLayout
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.yakclient.boot.archive.JpmResolutionProvider
import net.yakclient.boot.dependency.DependencyGraph
import net.yakclient.boot.dependency.DependencyGraphProvider
import net.yakclient.boot.dependency.DependencyProviders
import net.yakclient.boot.plugin.*
import net.yakclient.boot.plugin.artifact.PluginArtifactRequest
import net.yakclient.boot.plugin.artifact.PluginRepositorySettings
import net.yakclient.boot.store.CachingDataStore
import net.yakclient.boot.test.dependency.TestDependencyGraph.Companion.createMavenDependencyGraph
import net.yakclient.common.util.resolve
import java.nio.file.Path
import kotlin.io.path.writeBytes
import kotlin.test.Test

class PluginGraphTests {
//    @Test
//    fun `Test local plugin loading`() {
//        val dependencyGraph = createMavenDependencyGraph()
//
//        val basePath = Path.of(System.getProperty("user.dir")) resolve "cache/plugin"
//        val pluginRepoPath = Path.of(System.getProperty("user.dir")) resolve "cache/plugin-repo"
//
//        DependencyProviders.add(object :
//            DependencyGraphProvider<SimpleMavenArtifactRequest, SimpleMavenRepositorySettings> {
//            override val name: String = "simple-maven"
//            override val graph: DependencyGraph<SimpleMavenArtifactRequest, *, SimpleMavenRepositorySettings> =
//                dependencyGraph
//
//            override fun parseRequest(request: Map<String, String>): SimpleMavenArtifactRequest? {
//                val descriptorName = request["descriptor"] ?: return null
//                val isTransitive = request["isTransitive"] ?: "true"
//                val scopes = request["includeScopes"] ?: "compile,runtime,import"
//                val excludeArtifacts = request["excludeArtifacts"] ?: ""
//
//                return SimpleMavenArtifactRequest(
//                    SimpleMavenDescriptor.parseDescription(descriptorName) ?: return null,
//                    isTransitive.toBoolean(),
//                    scopes.split(',').toSet(),
//                    excludeArtifacts.split(',').toSet()
//                )
//            }
//
//            override fun parseSettings(settings: Map<String, String>): SimpleMavenRepositorySettings? {
//                val releasesEnabled = settings["releasesEnabled"] ?: "true"
//                val snapshotsEnabled = settings["snapshotsEnabled"] ?: "true"
//                val url = settings["url"] ?: return null
//                val preferredHash = settings["preferredHash"] ?: "SHA1"
//
//                val preferredHash1 = HashType.valueOf(preferredHash)
//
//                if (url == "plugin-local") return PluginRepositorySettings.local(
//                    path = pluginRepoPath.toAbsolutePath().toString()
//                )
//
//                return SimpleMavenRepositorySettings(
//                  SimpleMavenDefaultLayout(
//                        url,
//                        preferredHash1,
//                        releasesEnabled.toBoolean(),
//                        snapshotsEnabled.toBoolean()
//                    ),
//                    preferredHash1
//                )
//            }
//        })
//
//
//
//
//        val pluginGraph = PluginGraph(
//            basePath,
//            CachingDataStore(PluginDataAccess(basePath)),
//            JpmResolutionProvider,
//        )
//
//        val loader = pluginGraph.loaderOf(
//            PluginRepositorySettings.local(
//                path = pluginRepoPath.toAbsolutePath().toString()
//            )
//        )
//
//        val plugin = loader.load(
//            PluginArtifactRequest(
//                "net.yakclient.plugins:first-plugin:version"
//            )
//        )
//
//        println(plugin)
//    }

//    @Test
//    fun `Write to file`() {
//        val path =
//            "/Users/durgan/IdeaProjects/yakclient/boot/cache/plugin-repo/net/yakclient/plugins/first-plugin/version/first-plugin-version.prm.json"
//
//        val prm = PluginRuntimeModel(
//            "Test plugin",
//            PluginRuntimeModel.EMPTY_PACKAGING,
//            null,
//            listOf(
//                PluginRuntimeModelDependency(
//                    mapOf(
//                        "group" to "net.yakclient.plugins",
//                        "artifact" to "dependency-plugin",
//                        "version" to "1.0-SNAPSHOT"
//                    ),
//                    PluginRuntimeModelRepository(
//                        "plugin-runtime-repo",
//                        mapOf("url" to "file:///Users/durgan/IdeaProjects/yakclient/boot/cache/plugin-repo/")
//                    )
//                )
//            ),
//            listOf(
//                PluginRuntimeModelDependency(
//                    mapOf("descriptor" to "com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.13.4"),
//                    PluginRuntimeModelRepository(
//                        "simple-maven",
//                        mapOf("url" to MAVEN_CENTRAL_REPO)
//                    )
//                )
//            )
//        )
//
//        val mapper = ObjectMapper().registerModule(KotlinModule())
//
//        Path.of(path).writeBytes(mapper.writeValueAsBytes(prm))
//    }
}