package dev.extframework.boot.container.volume

import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path

public object RootVolume : ContainerVolume {
    override val name: String = "root"
    override val relativeRoot: Path = Path.of("/")
    override val parent: ContainerVolume? = null
    override val fs: FileSystem = FileSystems.getDefault()

    override fun derive(name: String, path: Path): ContainerVolume = DerivedVolume(name, path, RootVolume, /*fs.provider().newFileSystem(path.toUri(), mapOf<String, Any>()) */ fs)
}