package net.yakclient.boot

import com.durganmcbroom.artifact.resolver.ArtifactGraphProvider
import com.durganmcbroom.artifact.resolver.ArtifactResolutionOptions
import com.durganmcbroom.artifact.resolver.RepositorySettings
import com.durganmcbroom.artifact.resolver.group.ResolutionGroupConfig
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMaven
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactResolutionOptions
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
import net.yakclient.archives.ArchiveReference
import net.yakclient.archives.Archives
import net.yakclient.boot.container.ContainerHandle
import net.yakclient.boot.security.PrivilegeAccess
import net.yakclient.boot.security.PrivilegeManager
import net.yakclient.boot.container.volume.RootVolume
import net.yakclient.boot.dependency.JpmArchiveResolver
import net.yakclient.boot.archive.ArchiveStore
import net.yakclient.boot.dependency.ArchiveGraph1
import net.yakclient.boot.extension.DefaultExtensionManager
import net.yakclient.boot.extension.ExtensionProcess
import net.yakclient.boot.extension.yak.YakErmDependency
import net.yakclient.boot.extension.yak.YakExtManifestDependencyManager
import net.yakclient.boot.extension.yak.YakErmRepository
import net.yakclient.boot.extension.yak.YakExtensionLoader
import net.yakclient.boot.mixin.MixinRegistry
import net.yakclient.common.util.equalsAny
import java.nio.file.Files
import java.nio.file.Path
import java.security.Policy
import java.util.Properties

public fun main(args: Array<String>) {
    Policy.setPolicy(BasicPolicy())
    System.setSecurityManager(SecurityManager())

    val parser = ArgParser("boot")

    val appPath by parser.option(ArgType.String, "app").required()
    val dependencyCache by parser.option(ArgType.String, "dependency-cache-location").required()

    parser.parse(args)

    val instance = setupApp(appPath).createInstance()

    val dependencyGraph = ArchiveGraph1(ArchiveStore(Path.of(dependencyCache)), JpmArchiveResolver())

    val basicDependencyManager = object : YakExtManifestDependencyManager {
        override fun getProvider(repo: YakErmRepository): ArtifactGraphProvider<*, *> {
            check(
                repo.type.equalsAny(
                    "maven",
                    "maven-central",
                    "maven-local"
                )
            ) { "Only maven repositories are supported" }

            return SimpleMaven
        }

        override fun registerWith(config: ResolutionGroupConfig, repo: YakErmRepository) {
            check(
                repo.type.equalsAny(
                    "maven",
                    "maven-central",
                    "maven-local"
                )
            ) { "Only maven repositories are supported" }

            config.graphOf(SimpleMaven).register()
        }

        override fun getRepositorySettings(repo: YakErmRepository): RepositorySettings {
            check(
                repo.type.equalsAny(
                    "maven",
                    "maven-central",
                    "maven-local"
                )
            ) { "Only maven repositories are supported" }

            return SimpleMavenRepositorySettings().apply {
                when (repo.type) {
                    "maven-central" -> useMavenCentral()
                    "maven-local" -> useMavenLocal()
                    "maven" -> {
                        when (repo.configuration["layout"] ?: "default") {
                            "default" -> useDefaultLayout(
                                repo.configuration["url"]
                                    ?: throw IllegalStateException("No url specified for maven repository")
                            )
                            else -> throw IllegalStateException("Unknown layout: ${repo.configuration["layout"] ?: "default"}")
                        }
                    }
                }
                useBasicRepoReferencer()
            }
        }

        override fun getArtifactResolutionOptions(repo: YakErmRepository, dependency: YakErmDependency): ArtifactResolutionOptions {
            check(
                repo.type.equalsAny(
                    "maven",
                    "maven-central",
                    "maven-local"
                )
            ) { "Only maven repositories are supported" }

            val isTransitive: Boolean = dependency.options["transitive"] != "false"
            val exclude: List<String> = dependency.options["exclude"] as? List<String> ?: listOf()
            val includeScopes: List<String> = dependency.options["include-scopes"] as? List<String> ?: listOf()

            return SimpleMavenArtifactResolutionOptions(
                isTransitive = isTransitive,
                _excludes = exclude.toMutableSet(),
                _includeScopes = includeScopes.toMutableSet()
            )
        }

    }

    val manager = DefaultExtensionManager(YakExtensionLoader(basicDependencyManager,dependencyGraph ), MixinRegistry(mapOf()), instance)


    val rootPrivilegeManager = PrivilegeManager(
        null,
        PrivilegeAccess.allPrivileges(),
        ContainerHandle<ExtensionProcess>()
    ) { false }


    val container = manager.load(Path.of(""), rootPrivilegeManager, RootVolume, PrivilegeAccess.createPrivileges(), { true }, ClassLoader.getSystemClassLoader())

}

private const val APP_ENTRY_RESOURCE_LOCATION = "app.info"

private const val ENTRYPOINT_CLASS_NAME = "boot.app.class"

private fun setupApp(app: String): BootApplication {
    val path = Path.of(app)

    check(Files.exists(path)) { "Given argument 'app-entry' (value: '$app') cannot be found in the file system!" }

    val handle: ArchiveReference = Archives.find(path, Archives.Finders.ZIP_FINDER)
    val archive = Archives.resolve(handle, object : ClassLoader() {}, Archives.Resolvers.ZIP_RESOLVER).archive

    val properties = handle.reader[APP_ENTRY_RESOURCE_LOCATION]
        ?.let { Properties().apply { load(it.resource.open()) } }
        ?: throw IllegalStateException("Application Entry Point: '$app' should have a property file named: $'$APP_ENTRY_RESOURCE_LOCATION'")

    val entrypointCN = properties.getProperty(ENTRYPOINT_CLASS_NAME)

    val tryLoadClass = runCatching { archive.classloader.loadClass(entrypointCN) }

    val entrypointClass = tryLoadClass.getOrNull()
        ?: throw IllegalStateException("Failed to load class '$entrypointCN' from Entrypoint jar: '$app'")
    val entrypointConstructor = runCatching { entrypointClass.getConstructor() }.getOrNull()
        ?: throw IllegalStateException("ApplicationEntrypoint class: '$entrypointCN' must have a no-arg constructor!")
    val entrypoint = runCatching { entrypointConstructor.newInstance() }.getOrNull()
        ?: throw IllegalStateException("Failed to instantiate type: '$entrypointCN' during entyrpoint construction.")

    return entrypoint as? BootApplication
        ?: throw IllegalStateException("Type given as application entrypoint is not a child of '${BootApplication::class.java.name}'.")
}

