package daylightnebula.daylinmicroservices.filesystem

import daylightnebula.daylinmicroservices.Microservice
import daylightnebula.daylinmicroservices.MicroserviceConfig
import org.json.JSONObject

lateinit var service: Microservice

fun main(args: Array<String>) {
    // create map of the start arguments
    val argsMap = args.toList().chunked(2).associate { it[0] to it[1] }

    // create service config
    val config = MicroserviceConfig(
        "file_system",
        listOf("file_system")
    )

    // create microservice
    service = Microservice(config, endpoints = endpoints)

    // request file log from other file systems
    service.start()
    service.requestByName("file_system", "log", JSONObject())
        ?.whenComplete { json, _ -> FileSystemLog.updateLog(json) }

    // start while loop to keep everything alive
    while (true) {}
}