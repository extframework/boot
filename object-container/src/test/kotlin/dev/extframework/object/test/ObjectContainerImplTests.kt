package dev.extframework.`object`.test

import dev.extframework.`object`.ObjectContainerImpl
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ObjectContainerImplTests {
    @Test
    fun `Test add then find retrieves correct item`() {
        val container = ObjectContainerImpl<String>()

        container.register("test", "something")

        assertEquals(container.get("test"), "something")
        assert(container.has("test"))
    }

    @Test
    fun `Test objects return correct values`() {
        val container = ObjectContainerImpl<String>()
        assert(container.objects().isEmpty())

        container.register("test", "not sure")

        assert(container.objects().size == 1)
    }
}