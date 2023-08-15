package daylightnebula.daylinmicroservices.register

import daylightnebula.daylinmicroservices.core.Service
import daylightnebula.daylinmicroservices.redis.RedisConnection
import daylightnebula.daylinmicroservices.redis.RedisTable
import daylightnebula.daylinmicroservices.redis.RedisTableEntry
import daylightnebula.daylinmicroservices.redis.redisTable
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

var tablesInitialized = false

// redis entry and table containing service information
lateinit var services: RedisTable<ServiceEntry>
class ServiceEntry(
    uuid: UUID,
    val service: Service,
    val updateInterval: Long,
    val lastCheckTime: Long
): RedisTableEntry(uuid) {
    override fun toJson(): JSONObject = service.toJson()
        .put("updateInterval", updateInterval)
        .put("lastCheckTime", lastCheckTime)
}

// function that initializes all tables and starts redis connection
fun initTables() {
    RedisConnection.init()

    services = redisTable("services") { uuid, json ->
        ServiceEntry(
            uuid,
            Service(json),
            json.getLong("updateInterval"),
            json.getLong("lastCheckTime")
        )
    }

    tablesInitialized = true
}