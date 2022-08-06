package net.yakclient.boot.container.volume

import net.yakclient.common.util.resolve
import java.nio.file.Path

public fun ContainerVolume.absoluteRoot(): Path =
    parent?.let { it.absoluteRoot() resolve relativeRoot } ?: relativeRoot