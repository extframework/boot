package net.yakclient.boot

import net.yakclient.archives.transform.TransformerConfig

public interface AppInstance {
    public fun start(args: Array<String>)

    public fun applyMixin(to: String, config: TransformerConfig)
}