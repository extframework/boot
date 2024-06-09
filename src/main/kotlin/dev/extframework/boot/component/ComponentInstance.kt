package dev.extframework.boot.component

import com.durganmcbroom.jobs.Job

public interface ComponentInstance<T: ComponentConfiguration> {
    public fun start() : Job<Unit>

    public fun end() : Job<Unit>
}