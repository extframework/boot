package net.yakclient.boot.event

import com.durganmcbroom.event.api.Event
import com.durganmcbroom.event.api.EventPipeline
import com.durganmcbroom.event.api.EventStage
import kotlin.reflect.KClass

public class EventPipelineManager internal constructor() {
    private val stage = object : EventStage {
        val callbacks: MutableList<Pair<KClass<*>, BootEventHandler<*>>> = ArrayList()
        override val next: EventStage?
            get() = null

        override fun apply(event: Event): Event {
            check(event is BootEvent) {"Invalid event: '$event'. Must implement 'net.yakclient.boot.even.BootEvent'."}

            callbacks.filter {
                it.first.isInstance(event)
            }.forEach { (it.second as BootEventHandler<BootEvent>).accept(event) }

            return super.apply(event)
        }

    }
    internal val pipeline: EventPipeline = EventPipeline(stage)

    internal fun accept(event: BootEvent) {
        pipeline.accept(event)
    }

    public fun <T: BootEvent> subscribe(type: KClass<T>, callback: BootEventHandler<T>) {
        stage.callbacks.add(type to callback)
    }
}