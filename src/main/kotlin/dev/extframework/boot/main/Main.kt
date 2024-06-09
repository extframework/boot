package dev.extframework.boot.main

import com.durganmcbroom.resources.ResourceAlgorithm
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.cli.*
import dev.extframework.boot.archive.ArchiveException
import dev.extframework.boot.component.ComponentConfiguration
import dev.extframework.boot.component.ComponentFactory
import dev.extframework.boot.component.ComponentInstance
import dev.extframework.boot.component.SoftwareComponentModelRepository
import dev.extframework.boot.component.artifact.SoftwareComponentArtifactRequest
import dev.extframework.boot.component.artifact.SoftwareComponentDescriptor
import dev.extframework.boot.component.artifact.SoftwareComponentRepositorySettings
import dev.extframework.boot.component.context.impl.ContextNodeValueImpl
import runBootBlocking
import java.io.File
import java.nio.file.Path
import java.util.logging.Level
import java.util.logging.Logger

@ExperimentalCli
public fun main(args: Array<String>) {
    // Setup logger
    val logger = Logger.getLogger("boot")

    // Setup argument parser
    val parser = ArgParser("boot")

    // Get working dir
    val workingDir by parser.option(ArgType.String, "working-dir", "w").required()
    // Parse args

    // Create Boot context for later use
    val boot by lazy { ProductionBootInstance(Path.of(workingDir)) }

    fun echo(value: String) = logger.log(Level.INFO, value)

    // Start of cli commands
    class CacheComponent : Subcommand(
        "cache",
        "Installs a single software component into the cache for later use."
    ) {
        val descriptor by option(ArgType.String, "descriptor").required()
        val location by option(ArgType.String, "location").required()
        val type by option(ArgType.String, "type").default(SoftwareComponentModelRepository.DEFAULT)

        override fun execute() {
            echo("Setting up maven")

            val settings = when (type.lowercase()) {
                SoftwareComponentModelRepository.DEFAULT -> SoftwareComponentRepositorySettings.default(
                    location,
                    preferredHash = ResourceAlgorithm.SHA1
                )

                SoftwareComponentModelRepository.LOCAL -> SoftwareComponentRepositorySettings.local(
                    location,
                    preferredHash = ResourceAlgorithm.SHA1
                )

                else -> throw IllegalArgumentException("Unknown Software Component repository type: '$type'. Only known types are '${SoftwareComponentModelRepository.DEFAULT}' (for remote repositories) and '${SoftwareComponentModelRepository.LOCAL}' (for local repositories)")
            }

            val request = SoftwareComponentArtifactRequest(
                descriptor,
            )

            try {
                runBootBlocking {
                    boot.cache(request, settings)().merge()
                }
                echo("Successfully cached the component: '$descriptor'!")
            } catch (ex: ArchiveException) {
                echo("Failed to cache component, an error occurred. Throwing.")
                throw ex
            }
        }
    }

    class StartComponents : Subcommand(
        "start",
        "Starts a component and its children."
    ) {
        val component by argument(ArgType.String)
        val configPath by option(ArgType.String, "configuration", "c").default("")
        override fun execute() {
            runBootBlocking {

                val node = boot.archiveGraph.get(
                    checkNotNull(SoftwareComponentDescriptor.parseDescription(component)) { "Invalid component descriptor: '$component'" },
                    boot.componentResolver
                )().merge()

                echo("Parsing configuration: '$configPath'")
                val factory =
                    node.factory as? ComponentFactory<ComponentConfiguration, ComponentInstance<ComponentConfiguration>>
                        ?: throw IllegalArgumentException("Cannot start component: '$component' because it does not have a factory, is either a library or only a transitive component.")

                val configTree = if (configPath.isEmpty()) ContextNodeValueImpl(Unit) else ContextNodeValueImpl(
                    ObjectMapper().readValue<Any>(File(configPath))
                )

                val realConfig = node.factory.parseConfiguration(configTree)

                val instance = factory.new(realConfig)

                echo("Starting Instance")
                instance.start()
            }
        }
    }

    parser.subcommands(CacheComponent(), StartComponents())

    parser.parse(args)
}