package daylightnebula.daylinmicroservices.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.*

// class to contain info to represent a service
data class ServiceInfo(
    val id: UUID,
    val name: String,
    val tags: List<String>,
    val address: String,
    val doRegisterCheck: Boolean,
    val registerCheckIntervalMS: Long
) {
    constructor(json: JSONObject): this(
        UUID.fromString(json.getString("id")),
        json.getString("name"),
        json.getJSONArray("tags").map { it as String },
        json.getString("address"),
        json.getBoolean("doRegisterCheck"),
        json.getLong("registerCheckIntervalMS")
    )

    fun toJson() = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("tags", JSONArray().putAll(tags))
        .put("address", address)
        .put("doRegisterCheck", doRegisterCheck)
        .put("registerCheckIntervalMS", registerCheckIntervalMS)
}
