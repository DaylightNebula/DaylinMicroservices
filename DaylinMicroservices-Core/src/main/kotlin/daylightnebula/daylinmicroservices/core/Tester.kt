package daylightnebula.daylinmicroservices.core

import daylightnebula.daylinmicroservices.core.requests.request
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
        "ping" to (Schema() to { json ->
            Result.Ok(JSONObject().put("ping", "pong"))
        })
    )
)

val pingThread = loopingThread(1000) {
    service.getServices().forEach { (name, other) ->
        service.request(other, "ping", JSONObject()).whenComplete { result, _ ->
            if (result.isError()) println("PING FAILED WITH ERROR (sometimes false positive on initialize of test rig): ${result.error()}, Address: ${other.address}")
//            else println("ping success")
        }
    }
}

fun main(args: Array<String>) {
    service.start()
}