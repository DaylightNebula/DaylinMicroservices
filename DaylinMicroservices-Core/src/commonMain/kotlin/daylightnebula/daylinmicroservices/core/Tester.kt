package daylightnebula.daylinmicroservices.core

import daylightnebula.daylinmicroservices.serializables.DynamicObject
import daylightnebula.daylinmicroservices.serializables.Result
import daylightnebula.daylinmicroservices.serializables.Schema
import daylightnebula.daylinmicroservices.serializables.SchemaElement
import java.lang.Thread.sleep

val service = Microservice(
    MicroserviceConfig(
        "tester",
        "tester",
        listOf()
    ),
    endpoints = hashMapOf(
        "test" to (Schema("test" to SchemaElement.Boolean()) to { json ->
            Result.Ok(DynamicObject().put("test", true))
        })
    )
)
fun main(args: Array<String>) {
    service.start()

    while(true) {}
}