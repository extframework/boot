package net.yakclient.boot.plugin

public data class PluginRuntimeModel(
    val name: String,
    val packaging: String,
    val entrypoint: String?,
    val plugins: List<PluginRuntimeModelDependency>,
    val dependencies: List<PluginRuntimeModelDependency>
) {
    public companion object {
        public const val EMPTY_PACKAGING: String = "prm"
    }
}

public data class PluginRuntimeModelDependency(
    val request: Map<String, String>,
    val repository: PluginRuntimeModelRepository
) {
//    public fun toDescriptor() : PluginDescriptor = PluginDescriptor(groupId, artifactId, version, null)

//    public fun toKey() : PluginKey = PluginKey(toDescriptor())
}

public data class PluginRuntimeModelRepository(
    val type: String,
    val settings: Map<String, String>
) {
    public companion object {
        public const val PLUGIN_REPO_TYPE: String = "plugin-runtime-repo"

        public const val LOCAL_PLUGIN_REPO_TYPE: String = "local-plugin-runtime-repo"
    }
}