package net.yakclient.boot.component

public data class SoftwareComponentModel(
    val name: String,
    val packaging: String,
    val entrypoint: String?,
    val children: List<SoftwareComponentModelDependency>,
    val dependencies: List<SoftwareComponentModelDependency>
) {
    public companion object {
        public const val EMPTY_PACKAGING: String = "empty"
    }
}

public data class SoftwareComponentModelDependency(
    val request: Map<String, String>,
    val repository: SoftwareComponentModelRepository
) {
//    public fun toDescriptor() : PluginDescriptor = PluginDescriptor(groupId, artifactId, version, null)

//    public fun toKey() : PluginKey = PluginKey(toDescriptor())
}

public data class SoftwareComponentModelRepository(
    val type: String,
    val settings: Map<String, String>
) {
    public companion object {
        public const val DEFAULT: String = "default"

        public const val LOCAL: String = "local"
    }
}