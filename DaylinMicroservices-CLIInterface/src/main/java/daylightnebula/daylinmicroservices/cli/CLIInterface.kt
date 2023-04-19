package daylightnebula.daylinmicroservices.cli

import daylightnebula.daylinmicroservices.Microservice
import daylightnebula.daylinmicroservices.MicroserviceConfig
import daylightnebula.daylinmicroservices.requests.requestByName
import org.json.JSONObject
import java.lang.StringBuilder
import java.lang.Thread.sleep

val service = Microservice(MicroserviceConfig("cli-interface", "cli-interface", tags = listOf("cli")), endpoints = hashMapOf())
var running = true

fun main() {
    // get service
    service.start()

    // run command while running
    sleep(500)
    while(running) runCommand()

    // stop service
    service.dispose()
}

fun runCommand() {
    // get new command
    val command = readln()

    // tokenize the command
    val tokens = tokenize(command)

    // if not tokens error and return
    if (tokens.isEmpty()) {
        println("Could not tokenize command \"$command\"")
        return
    }

    // run command
    when (tokens.first()) {
        "request" -> {
            // make sure we have enough args
            if (tokens.size < 4) {
                println("Format: request <target uuid or name> <endpoint> <json>")
                return
            }

            // make sure json is valid
            val json = try { JSONObject(tokens[3]) } catch (ex: Exception) { null }
            if (json == null) {
                println("Json is not valid!")
                return
            }

            // send request
            service.requestByName(
                tokens[1],
                tokens[2],
                json
            )?.whenComplete { result, _ ->
                if (result.isError()) println("Request returned error: ${result.getError()}")
                else println("Response ${result.getResult().toString(4)}")
            } ?: println("No service named ${tokens[1]}")
        }
        "info" -> {
            if (tokens.size < 2) {
                println("Format: info <target service uuid or name>")
                return
            }

            // make sure target is valid
            val targetService = service.getServiceWithName(tokens[1])?.value
            if (targetService == null) {
                println("No service could be identified with ${tokens[1]}")
                return
            }

            // print info for the target service
            println("Name: ${targetService.service}")
            println("Address: ${targetService.address}")
            println("Port: ${targetService.port}")
        }
        "services" -> println("Current services: ${service.getServices().map { it.value.service }}")
        "stop" -> running = false
        else -> println("Invalid/unknown command \"$command\"")
    }
}

val startGroupChars = arrayOf('{', '[', '(')
val stopGroupChars = arrayOf('}', ']', ')')
var lastQuoteState = false
fun tokenize(str: String): List<String> {
    // setup variables to keep track of the current buffer
    val output = mutableListOf<String>()
    var curBuilder = StringBuilder()
    var curDepth = 0

    // loop through each character in the string
    for (char in str) {
        // check if start and stop character
        val isStart = startGroupChars.contains(char)
        val isStop = !isStart && stopGroupChars.contains(char)

        // change depth
        if (isStart) curDepth++
        else if (isStop) curDepth--

        // custom handle for quotations
        if (char == '"') {
            if (lastQuoteState) {
                lastQuoteState = false
                curDepth--
            } else {
                lastQuoteState = true
                curDepth++
            }
        }

        // if depth is less than 0, return blank array as there was an error
        if (curDepth < 0) return listOf()

        // if space and depth is 0, split, otherwise just add to current builder
        if (char == ' ' && curDepth == 0) {
            output.add(curBuilder.toString())
            curBuilder = StringBuilder()
        } else
            curBuilder.append(char)
    }

    // add current builder to output
    output.add(curBuilder.toString())

    // pass back output
    return output
}