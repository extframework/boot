package dev.extframework.boot.container.volume

import java.nio.file.FileSystem
import java.nio.file.Path

public class VolumeRouter(
    override val parent: ContainerVolume,
    rules: RouterRules,
) : ContainerVolume {
    override val name: String by parent::name
    override val relativeRoot: Path = Path.of("")
    override val fs: FileSystem = RouterFileSystem(parent.fs, rules)

    override fun derive(name: String, path: Path): ContainerVolume = parent.derive(name, path)
}