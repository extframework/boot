package net.yakclient.boot.extension.yak


// Represents the YakClient ERM (or Extension runtime model)
public data class YakErm(
    val groupId: String,
    val name: String,
    val version: String,

    val packagingType: String, // Jar, War, Zip, etc...

    val extensionClass: String,
    val stateHolderClass: String?,

    val repositories: List<YakErmRepository>,
    val dependencies: List<YakErmDependency>,

    val extensionRepositories: List<YakErmRepository>,
    val extensionDependencies: List<String>,
) {
}

public data class YakErmDependency(
    val notation: String,
    val options: Options
) {
    public data class Options(
        val isTransitive: Boolean,
        val exclude: Set<String>,
    )
}

public data class YakErmRepository(
    val type: String,
    val configuration: Configuration
) {
    public data class Configuration(
        val url: String?
    )
}