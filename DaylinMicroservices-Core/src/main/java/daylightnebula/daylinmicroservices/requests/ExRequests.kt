package daylightnebula.daylinmicroservices.requests

import com.orbitz.consul.model.health.Service
import daylightnebula.daylinmicroservices.Microservice
import org.json.JSONObject
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

// make request to services
fun Microservice.request(service: Service, endpoint: String, json: JSONObject): CompletableFuture<JSONObject> {
    json.put("broadcast", false)
    val address = "http://${service.address}:${service.port}/$endpoint"
    return Requester.rawRequest(this.config.logger, address, json).completeOnTimeout(JSONObject(), 1, TimeUnit.SECONDS)
}
fun Microservice.requestByUUID(uuid: UUID, endpoint: String, json: JSONObject): CompletableFuture<JSONObject>? {
    val service = getService(uuid) ?: return null
    return request(service, endpoint, json).completeOnTimeout(JSONObject(), 1, TimeUnit.SECONDS)
}
fun Microservice.requestByName(name: String, endpoint: String, json: JSONObject): CompletableFuture<JSONObject>? {
    val serviceEntry = getServiceWithName(name) ?: return null
    return request(serviceEntry.value, endpoint, json).completeOnTimeout(JSONObject(), 1, TimeUnit.SECONDS)
}
fun Microservice.requestByTag(tag: String, endpoint: String, json: JSONObject): CompletableFuture<JSONObject>? {
    val serviceEntry = getServiceWithTag(tag) ?: return null
    return request(serviceEntry.value, endpoint, json).completeOnTimeout(JSONObject(), 1, TimeUnit.SECONDS)
}

// broadcast to services
fun Microservice.broadcastRequestByName(name: String, endpoint: String, json: JSONObject): List<CompletableFuture<JSONObject>> {
    json.put("broadcast", true)
    return getServicesWithName(name).map { entry -> request(entry.value, endpoint, json) }
}
fun Microservice.broadcastRequestByTag(tag: String, endpoint: String, json: JSONObject): List<CompletableFuture<JSONObject>> {
    json.put("broadcast", true)
    return getServicesWithTag(tag).map { entry -> request(entry.value, endpoint, json) }
}

// when one of the futures completes, all the others will be cancelled and then the callback is called
fun List<CompletableFuture<JSONObject>>.firstSuccessfulComplete(callback: (json: JSONObject) -> Unit) {
    this.forEach { future ->
        future.whenComplete { json, _ ->
            if (json.has(".cancel")) return@whenComplete

            this.forEach { it.complete(JSONObject().put(".cancel", true)) }

            callback(json)
        }
    }
}