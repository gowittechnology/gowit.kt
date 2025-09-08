package com.gowit.sdk.core

import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.gowit.sdk.model.EventType
import java.lang.reflect.Type

/**
 * JSON serialization utilities for Gowit SDK
 */
internal object JsonSerializer {
    private val gson: Gson by lazy {
        GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .registerTypeAdapter(EventType::class.java, EventTypeAdapter())
            .create()
    }

    fun toJson(obj: Any): String {
        return gson.toJson(obj)
    }

    inline fun <reified T> fromJson(json: String): T {
        return gson.fromJson(json, T::class.java)
    }

    fun <T> fromJson(
        json: String,
        clazz: Class<T>,
    ): T {
        return gson.fromJson(json, clazz)
    }

    /**
     * Custom adapter for EventType enum
     */
    private class EventTypeAdapter : JsonSerializer<EventType>, JsonDeserializer<EventType> {
        override fun serialize(
            src: EventType,
            typeOfSrc: Type,
            context: JsonSerializationContext,
        ): JsonElement {
            return context.serialize(src.value)
        }

        override fun deserialize(
            json: JsonElement,
            typeOfT: Type,
            context: JsonDeserializationContext,
        ): EventType {
            val value = json.asString
            return EventType.fromString(value)
                ?: throw IllegalArgumentException("Unknown event type: $value")
        }
    }
}
