package daylightnebula.daylinmicroservices

import mu.KLogger
import mu.KotlinLogging
import org.json.JSONObject
import org.slf4j.Logger
import java.io.File
import java.lang.IllegalArgumentException

// just a basic config that can load from json or file
data class MicroserviceConfig(
    val name: String,           // the name of the microservice
    val tags: List<String>,     // the tags of the microservice, like what its type is
    var port: Int,              // the port that this service will run on
    val consulUrl: String,      // the url that consul is on
    val consulRefUrl: String,   // this is what consul will use to reference this microservice
    val logger: Logger =        // the logger that this service will write its output too
        KotlinLogging.logger("Microservice $name")
) {
    // load from a json object
    constructor(json: JSONObject): this(
        json.optString("name", ""),
        json.optJSONArray("tags")?.map { it as String } ?: listOf(),
        json.optInt("port", 0),
        json.optString("consulUrl", "http://localhost:8500"),
        json.optString("consulRefUrl", "http://host.docker.internal:${json.optInt("port", 0)}/")
    )

    // load from a json object in a file
    constructor(file: File): this(
        if (file.exists()) JSONObject(file.readText()) else throw IllegalArgumentException("Config file given must exist!  Path: ${file.absolutePath}")
    )
}