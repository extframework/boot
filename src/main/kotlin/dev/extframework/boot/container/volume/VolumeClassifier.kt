package dev.extframework.boot.container.volume

import java.nio.file.Path

public class VolumeClassifier(
    volume: ContainerVolume
) : PathClassifier {
    private val root = volume.absoluteRoot()
    override fun classify(path: Path): ClassifiedPath {
        return if (path.startsWith(root)) ClassifiedPath(root, true) else ClassifiedPath()
    }
}