package daylightnebula.daylinmicroservices

import java.lang.Thread.sleep

val service = Microservice(
    MicroserviceConfig(
        "tester",
        "tester",
        listOf()
    ),
    endpoints = hashMapOf()
)
fun main(args: Array<String>) {
    service.start()

    while(true) {}
//    sleep(10000)
//    service.dispose()
}