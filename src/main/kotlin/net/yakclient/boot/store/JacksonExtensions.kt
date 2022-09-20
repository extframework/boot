package net.yakclient.boot.store

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import kotlin.reflect.KClass

public fun <T: Any> SimpleModule.addSerializer(type: KClass<T>, function: (T, gen: JsonGenerator, provider: SerializerProvider) -> Unit) : SimpleModule {
    addSerializer(type.java, object : StdSerializer<T>(type.java) {
        override fun serialize(value: T, gen: JsonGenerator, provider: SerializerProvider) {
            function(value, gen, provider)
        }
    })

    return this
}

public fun <T: Any> SimpleModule.addDeserializer(type: KClass<T>, function: (parser: JsonParser, context: DeserializationContext) -> T) : SimpleModule {
    addDeserializer(type.java, object : StdDeserializer<T>(type.java) {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): T =function(p, ctxt)
    })

    return this
}