package dev.extframework.boot.maven

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.resources.ResourceAlgorithm
import dev.extframework.boot.dependency.DependencyResolverProvider

public class MavenResolverProvider :
    DependencyResolverProvider<SimpleMavenDescriptor, SimpleMavenArtifactRequest, SimpleMavenRepositorySettings> {
    override val name: String = "simple-maven"
    override val resolver: MavenDependencyResolver = MavenDependencyResolver(
        parentClassLoader = MavenResolverProvider::class.java.classLoader,
    )

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

        val hashType = ResourceAlgorithm.valueOf(preferredHash)

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