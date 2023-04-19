package daylightnebula.daylinmicroservices.requests

import daylightnebula.daylinmicroservices.endpoints.EndpointResult
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.slf4j.Logger
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread

internal object Requester {
    fun rawRequest(logger: Logger, address: String, json: JSONObject): CompletableFuture<EndpointResult> {
        val future = CompletableFuture<EndpointResult>()

        // start a thread to run the request async
        thread {
            runBlocking {
                // send get request with timeout, if any error occurs, just return null
                val response = try {
                    HttpClient(CIO) {
                        install(HttpTimeout) {
                            requestTimeoutMillis = 3000
                        }
                    }.get(address, block = {
                        parameter("json", json.toString(1))
                    })
                } catch (ex: Exception) { logger.warn("Request failed with exception: ${ex.message}"); null }

                // when request completes, call the on complete function
                future.complete(EndpointResult(JSONObject(response?.bodyAsText() ?: "{}")))
            }
        }

        return future
    }
}