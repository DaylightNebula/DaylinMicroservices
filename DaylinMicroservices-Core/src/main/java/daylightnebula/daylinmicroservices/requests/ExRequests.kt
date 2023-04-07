package daylightnebula.daylinmicroservices.requests

import com.orbitz.consul.model.health.Service
import daylightnebula.daylinmicroservices.Microservice
import org.json.JSONObject
import java.util.*
import java.util.concurrent.CompletableFuture

// make request to services
fun Microservice.request(service: Service, endpoint: String, json: JSONObject): CompletableFuture<JSONObject> {
    json.put("broadcast", false)
    val address = "http://${service.address}:${service.port}/$endpoint"
    return Requester.rawRequest(this.config.logger, address, json)
}
fun Microservice.requestByUUID(uuid: UUID, endpoint: String, json: JSONObject): CompletableFuture<JSONObject>? {
    val service = getService(uuid) ?: return null
    return request(service, endpoint, json)
}
fun Microservice.requestByName(name: String, endpoint: String, json: JSONObject): CompletableFuture<JSONObject>? {
    val serviceEntry = getServiceWithName(name) ?: return null
    return request(serviceEntry.value, endpoint, json)
}
fun Microservice.requestByTag(tag: String, endpoint: String, json: JSONObject): CompletableFuture<JSONObject>? {
    val serviceEntry = getServiceWithTag(tag) ?: return null
    return request(serviceEntry.value, endpoint, json)
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