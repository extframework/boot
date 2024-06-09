package dev.extframework.boot.container.volume

import dev.extframework.common.util.resolve
import java.nio.file.Path

public fun ContainerVolume.absoluteRoot(): Path =
    parent?.let { it.absoluteRoot() resolve relativeRoot } ?: relativeRoot