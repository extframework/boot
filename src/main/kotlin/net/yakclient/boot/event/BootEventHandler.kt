package net.yakclient.boot.event

import com.durganmcbroom.event.api.EventCallback

public interface BootEventHandler<in T: BootEvent> : EventCallback<T>