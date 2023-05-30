package daylightnebula.daylinmicroservices.core.requests

import com.orbitz.consul.model.health.Service
import daylightnebula.daylinmicroservices.core.Microservice
import daylightnebula.daylinmicroservices.serializables.DynamicObject
import daylightnebula.daylinmicroservices.serializables.Schema
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

// make request to services
fun Microservice.request(service: Service, endpoint: String, schema: Schema, json: DynamicObject): CompletableFuture<daylightnebula.daylinmicroservices.serializables.Result<DynamicObject>> {
    json.put("broadcast", false)
    val address = "http://${service.address}:${service.port}/$endpoint"
    return Requester.rawRequest(this.config.logger, address, schema, json)
        .completeOnTimeout(daylightnebula.daylinmicroservices.serializables.Result.Error<DynamicObject>("Timeout"), 1, TimeUnit.SECONDS)
}
fun Microservice.requestByUUID(uuid: String, endpoint: String, schema: Schema, json: DynamicObject): CompletableFuture<daylightnebula.daylinmicroservices.serializables.Result<DynamicObject>>? {
    val service = getService(uuid) ?: return null
    return request(service, endpoint, schema, json)
        .completeOnTimeout(daylightnebula.daylinmicroservices.serializables.Result.Error<DynamicObject>("Timeout"), 1, TimeUnit.SECONDS)
}
fun Microservice.requestByName(name: String, endpoint: String, schema: Schema, json: DynamicObject): CompletableFuture<daylightnebula.daylinmicroservices.serializables.Result<DynamicObject>>? {
    val serviceEntry = getServiceWithName(name) ?: return null
    return request(serviceEntry.value, endpoint, schema, json)
        .completeOnTimeout(daylightnebula.daylinmicroservices.serializables.Result.Error<DynamicObject>("Timeout"), 1, TimeUnit.SECONDS)
}
fun Microservice.requestByTag(tag: String, endpoint: String, schema: Schema, json: DynamicObject): CompletableFuture<daylightnebula.daylinmicroservices.serializables.Result<DynamicObject>>? {
    val serviceEntry = getServiceWithTag(tag) ?: return null
    return request(serviceEntry.value, endpoint, schema, json)
        .completeOnTimeout(daylightnebula.daylinmicroservices.serializables.Result.Error<DynamicObject>("Timeout"), 1, TimeUnit.SECONDS)
}

// broadcast to services
fun Microservice.broadcastRequestByName(name: String, endpoint: String, schema: Schema, json: DynamicObject): List<CompletableFuture<daylightnebula.daylinmicroservices.serializables.Result<DynamicObject>>> {
    json.put("broadcast", true)
    return getServicesWithName(name).map { entry -> request(entry.value, endpoint, schema, json) }
}
fun Microservice.broadcastRequestByTag(tag: String, endpoint: String, schema: Schema, json: DynamicObject): List<CompletableFuture<daylightnebula.daylinmicroservices.serializables.Result<DynamicObject>>> {
    json.put("broadcast", true)
    return getServicesWithTag(tag).map { entry -> request(entry.value, endpoint, schema, json) }
}

// when one of the futures completes, all the others will be cancelled and then the callback is called
fun List<CompletableFuture<daylightnebula.daylinmicroservices.serializables.Result<DynamicObject>>>.firstSuccessfulComplete(callback: (result: daylightnebula.daylinmicroservices.serializables.Result<DynamicObject>) -> Unit) {
    this.forEach { future ->
        future.whenComplete { result, _ ->
            // if error, cancel
            if (result.isError()) return@whenComplete

            // complete all other futures with an error
            this.forEach { it.complete(daylightnebula.daylinmicroservices.serializables.Result.Error("Cancelled by firstSuccessfulComplete")) }

            // call callback
            callback(result)
        }
    }
}