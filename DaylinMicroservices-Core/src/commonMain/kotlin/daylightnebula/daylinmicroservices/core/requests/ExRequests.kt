package daylightnebula.daylinmicroservices.core.requests

import com.orbitz.consul.model.health.Service
import daylightnebula.daylinmicroservices.core.Microservice
import daylightnebula.daylinmicroservices.serializables.Result
import org.json.JSONObject
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

// make request to services
fun Microservice.request(service: Service, endpoint: String, json: JSONObject): CompletableFuture<Result<JSONObject>> {
    json.put("broadcast", false)
    val address = "http://${service.address}:${service.port}/$endpoint"
    return Requester.rawRequest(this.config.logger, address, json)
        .completeOnTimeout(Result.Error<JSONObject>("Timeout"), 1, TimeUnit.SECONDS)
}
fun Microservice.requestByUUID(uuid: String, endpoint: String, json: JSONObject): CompletableFuture<Result<JSONObject>>? {
    val service = getService(uuid) ?: return null
    return request(service, endpoint, json).completeOnTimeout(Result.Error<JSONObject>("Timeout"), 1, TimeUnit.SECONDS)
}
fun Microservice.requestByName(name: String, endpoint: String, json: JSONObject): CompletableFuture<Result<JSONObject>>? {
    val serviceEntry = getServiceWithName(name) ?: return null
    return request(serviceEntry.value, endpoint, json).completeOnTimeout(Result.Error<JSONObject>("Timeout"), 1, TimeUnit.SECONDS)
}
fun Microservice.requestByTag(tag: String, endpoint: String, json: JSONObject): CompletableFuture<Result<JSONObject>>? {
    val serviceEntry = getServiceWithTag(tag) ?: return null
    return request(serviceEntry.value, endpoint, json).completeOnTimeout(Result.Error<JSONObject>("Timeout"), 1, TimeUnit.SECONDS)
}

// broadcast to services
fun Microservice.broadcastRequestByName(name: String, endpoint: String, json: JSONObject): List<CompletableFuture<Result<JSONObject>>> {
    json.put("broadcast", true)
    return getServicesWithName(name).map { entry -> request(entry.value, endpoint, json) }
}
fun Microservice.broadcastRequestByTag(tag: String, endpoint: String, json: JSONObject): List<CompletableFuture<Result<JSONObject>>> {
    json.put("broadcast", true)
    return getServicesWithTag(tag).map { entry -> request(entry.value, endpoint, json) }
}

// when one of the futures completes, all the others will be cancelled and then the callback is called
fun List<CompletableFuture<Result<JSONObject>>>.firstSuccessfulComplete(callback: (json: Result<JSONObject>) -> Unit) {
    this.forEach { future ->
        future.whenComplete { result, _ ->
            // if error, cancel
            if (result.isError()) return@whenComplete

            // complete all other futures with an error
            this.forEach { it.complete(Result.Error<JSONObject>("Cancelled by firstSuccessfulComplete")) }

            // call callback
            callback(result)
        }
    }
}