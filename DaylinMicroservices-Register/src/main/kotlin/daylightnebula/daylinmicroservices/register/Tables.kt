package daylightnebula.daylinmicroservices.register

import daylightnebula.daylinmicroservices.core.Service
import org.json.JSONObject
import java.util.*

// redis entry and table containing service information
data class ServiceEntry(
    val service: Service,
    val updateInterval: Long,
    val lastCheckTime: Long
) {
    constructor(json: JSONObject): this(
        Service(json),
        json.getLong("updateInterval"),
        json.getLong("lastCheckTime")
    )

    fun toJson(): JSONObject = service.toJson()
        .put("updateInterval", updateInterval)
        .put("lastCheckTime", lastCheckTime)
}