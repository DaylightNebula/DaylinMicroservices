package daylightnebula.daylinmicroservices.filesystem

import daylightnebula.daylinmicroservices.Microservice
import daylightnebula.daylinmicroservices.MicroserviceConfig
import org.json.JSONObject

lateinit var service: Microservice
lateinit var name: String

fun main(args: Array<String>) {
    // create map of the start arguments
    val argsMap = args.toList().chunked(2).associate { it[0] to it[1] }
    val name = argsMap["-name"] ?: "file_system"

    // create service config
    val config = MicroserviceConfig(
        name,
        listOf("file_system")
    )

    // create microservice
    service = Microservice(config, endpoints = endpoints)

    // request file log from other file systems
    service.start()
    service.requestByName(name, "log", JSONObject())
        ?.whenComplete { json, _ -> FileSystemFiles.updateLog(json) }

    FileSystemFiles.trigger()

    // start while loop to keep everything alive
    while (true) {}
}