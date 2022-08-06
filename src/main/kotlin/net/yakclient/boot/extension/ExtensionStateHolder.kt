package net.yakclient.boot.extension

import net.yakclient.boot.mixin.InjectionMetadata

public interface ExtensionStateHolder {
    public val injections: Map<String, InjectionMetadata>
    public val enabled: Boolean
}
