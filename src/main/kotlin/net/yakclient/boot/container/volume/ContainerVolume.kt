package net.yakclient.boot.container.volume

import java.nio.file.FileSystem
import java.nio.file.Path

public interface ContainerVolume {
    public val name: String
    public val relativeRoot: Path
    public val parent: ContainerVolume?
    public val fs: FileSystem

    public fun derive(name: String, path: Path) : ContainerVolume
}
