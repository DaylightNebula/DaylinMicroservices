package daylightnebula.daylinmicroservices

import java.lang.Thread.sleep

val service = Microservice(
    MicroserviceConfig(
    "tester",
    listOf()
), endpoints = hashMapOf())
fun main(args: Array<String>) {
    service.start()

    sleep(10000)
    service.dispose()
}