package net.yakclient.boot.component

import net.yakclient.boot.BootContext

public data class ComponentContext(
    val configuration: Map<String, String>,
    val bootContext: BootContext
)