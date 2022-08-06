package net.yakclient.boot.extension.artifact

import com.durganmcbroom.artifact.resolver.ArtifactGraphConfig
import net.yakclient.boot.extension.yak.artifact.YakExtArtifactResolutionOptions
import net.yakclient.boot.extension.yak.artifact.YakExtDescriptor

public abstract class ExtensionGraphConfig : ArtifactGraphConfig<YakExtDescriptor, YakExtArtifactResolutionOptions>()