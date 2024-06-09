package dev.extframework.boot.container.volume

import java.nio.file.Path

public fun interface PathClassifier {
    public fun classify(path: Path): ClassifiedPath
}

public data class ClassifiedPath(
    private val _classified: Path? = null,
    public val couldClassify: Boolean = false,
) {
    public val classified: Path
        get() = _classified ?: throw UnsupportedOperationException("Path could not be classified!")

    public fun isMoreSpecific(other: ClassifiedPath): Boolean {
        return if (classified.startsWith(other.classified)) false
        else other.classified.startsWith(classified)
    }
}