package dev.extframework.boot.test.blackbox

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.SuccessfulJob
import com.durganmcbroom.jobs.job
import dev.extframework.boot.component.ComponentInstance

public class TestComponent : ComponentInstance<TestComponentConfiguration> {
    public var isOn: Boolean = false
        private set

    override fun start(): Job<Unit> = job {
        isOn = true
    }

    override fun end(): Job<Unit> = job {
        isOn = false
    }
}