package daylightnebula.daylinmicroservices.core

import daylightnebula.daylinmicroservices.serializables.Result
import daylightnebula.daylinmicroservices.serializables.Schema
import org.json.JSONObject
import java.lang.Thread.sleep
import java.util.*

val service = Microservice(
    MicroserviceConfig(
        UUID.randomUUID(),
        "tester",
        listOf()
    ),
    endpoints = hashMapOf(
        "test" to (Schema() to { json ->
            Result.Ok(JSONObject().put("test", true))
        })
    )
)
fun main(args: Array<String>) {
    service.start()

    while(true) {}
//    sleep(10000)
//    service.dispose()
}