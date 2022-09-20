package net.yakclient.boot.plugin.artifact

import com.durganmcbroom.artifact.resolver.RepositoryFactory

public object BootPlugins : RepositoryFactory<PluginRepositorySettings, PluginArtifactRequest, PluginArtifactStub, PluginArtifactReference, PluginArtifactRepository> {
    override fun createNew(settings: PluginRepositorySettings): PluginArtifactRepository = PluginArtifactRepository(settings, this)
}