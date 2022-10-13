package net.yakclient.boot.event

import com.durganmcbroom.event.api.EventCallback

public fun interface BootEventHandler<in T: BootEvent> : EventCallback<T>