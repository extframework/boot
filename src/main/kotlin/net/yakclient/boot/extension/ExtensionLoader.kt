package net.yakclient.boot.extension

import net.yakclient.boot.container.ProcessLoader
import java.nio.file.Path

public interface ExtensionLoader<T : ExtensionInfo> : ProcessLoader<T, ExtensionProcess> {
}