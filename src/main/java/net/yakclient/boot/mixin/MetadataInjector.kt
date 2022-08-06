package net.yakclient.boot.mixin

import net.yakclient.archives.transform.TransformerConfig

public interface MetadataInjector<T: InjectionMetadata> {
    public fun inject(meta: T) : TransformerConfig
}
