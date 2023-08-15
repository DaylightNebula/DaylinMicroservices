package daylightnebula.daylinmicroservices.core

import daylightnebula.daylinmicroservices.serializables.Result
import daylightnebula.daylinmicroservices.serializables.Schema
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

fun endpoint(
    name: String,
    schema: Schema,
    callback: (json: JSONObject) -> Result<JSONObject>
) = name to (schema to callback)

enum class ServiceEvent {
    ADDED,
    REMOVED
}

// data class containing all information relating to a service
data class Service(
    val id: UUID,
    val name: String,
    val tags: List<String>,
    val address: String,
    val metadata: Map<String, String>,
    val updateInterval: Long
) {
    constructor(json: JSONObject): this(
        UUID.fromString(json.getString("id"))!!,
        json.getString("name"),
        json.getJSONArray("tags").map { it as String },
        json.getString("address"),
        json.getJSONObject("metadata").toMap().mapValues { it as String },
        json.getLong("updateInterval")
    )

    fun toJson() = JSONObject()
        .put("id", id.toString())
        .put("name", name)
        .put("tags", JSONArray().putAll(tags))
        .put("address", address)
        .put("metadata", JSONObject(metadata))
        .put("updateInterval", updateInterval)
}