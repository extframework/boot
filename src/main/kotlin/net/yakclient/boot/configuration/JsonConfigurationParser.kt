package net.yakclient.boot.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.io.InputStream
import kotlin.reflect.KClass

public class JsonConfigurationParser<T : BootConfiguration>(
    private val type: KClass<T>,
) : ConfigurationParser<InputStream, T> {
    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    override fun parse(value: InputStream): T {
        return mapper.readValue(value, type.java)
    }
}