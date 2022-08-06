package net.yakclient.boot.extension

import net.yakclient.boot.AppInstance
import net.yakclient.boot.container.Container
import net.yakclient.boot.container.ContainerLoader
import net.yakclient.boot.container.callerContainer
import net.yakclient.boot.security.PrivilegeGrantRequestHandler
import net.yakclient.boot.security.PrivilegeManager
import net.yakclient.boot.security.Privileges
import net.yakclient.boot.container.volume.ContainerVolume
import net.yakclient.boot.mixin.InjectionMetadata
import net.yakclient.boot.mixin.MixinRegistry
import java.nio.file.Path

public abstract class ExtensionManager<T : ExtensionInfo>(
    public val loader: ExtensionLoader<T>,
    public val mixinRegistry: MixinRegistry,
    public val app: AppInstance,
) {
    private val _extensions: MutableMap<ExtKey, ExtensionStateHolder> = HashMap()
    public val extensions: Map<ExtKey, ExtensionStateHolder>
        get() = _extensions.toMap()

    internal fun load(
        path: Path,
        parentManager: PrivilegeManager,
        volume: ContainerVolume,
        privileges: Privileges,
        granter: PrivilegeGrantRequestHandler,
        parentClassLoader: ClassLoader
    ): Container<ExtensionProcess> {
        val extInfo = loader.loadInfo(path)

        val handle = ContainerLoader.createHandle<ExtensionProcess>()

        val container = ContainerLoader.load(
            extInfo,
            handle,
            loader,
            volume,
            PrivilegeManager(parentManager, privileges, handle, granter),
            parentClassLoader
        )

        val process = container.process
        val stateHolder = process.stateHolder

        val extension = process.extension

        val key = generateKey(extension)
        _extensions[key] = stateHolder

        stateHolder.injections
            .mapValues { transformInjection(key, it.value) }
            .forEach { mixinRegistry.register(it.key, it.value) }

        stateHolder.injections
            .mapTo(HashSet(), Map.Entry<String, InjectionMetadata>::key)
            .map { it to (mixinRegistry.transformerFor(it)) }
            .forEach { app.applyMixin(it.first, it.second) }

        return container
    }

    public abstract fun transformInjection(key: ExtKey, metadata: InjectionMetadata): InjectionMetadata

    public abstract fun generateKey(ext: Extension): ExtKey

    public interface ExtKey
}