package net.yakclient.boot.container.volume

import java.nio.file.Path

public class PathNameClassifier(
    private val classifiableNames: List<Path>
) : PathClassifier {
    public constructor(vararg classifiableNames: String) : this(classifiableNames.map(Path::of))
    public constructor(vararg classifiablePaths: Path) : this(classifiablePaths.toList())

    override fun classify(path: Path): ClassifiedPath =
        classifiableNames.firstOrNull { it == path }?.let {ClassifiedPath(it, true)} ?: ClassifiedPath()
}

private const val CP_NAME = "java.class.path"
private const val MP_NAME = "jdk.module.path"
private const val SPLITTER_NAME = "path.separator"
private val PATH_SPLITTER = System.getProperty(SPLITTER_NAME)

private fun splitProperty(name: String): List<String> = (System.getProperty(name)?.split(PATH_SPLITTER))?.filter { it.isBlank() } ?: listOf()

public val ClassPathClassifier: PathNameClassifier = PathNameClassifier((splitProperty(CP_NAME) + splitProperty(MP_NAME)).map(Path::of))