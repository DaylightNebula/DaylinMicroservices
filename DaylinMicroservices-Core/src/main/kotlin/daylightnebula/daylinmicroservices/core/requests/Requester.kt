package daylightnebula.daylinmicroservices.core.requests

import daylightnebula.daylinmicroservices.serializables.Result
import daylightnebula.daylinmicroservices.serializables.toResult
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import org.json.JSONException
import org.json.JSONObject
import org.slf4j.Logger
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread

internal object Requester {
    fun rawRequest(logger: Logger, address: String, json: JSONObject): CompletableFuture<Result<JSONObject>> {
        val future = CompletableFuture<Result<JSONObject>>()

        // start a thread to run the request async
        thread {
            runBlocking {
                // send get request with timeout, if any error occurs, just return null
                val response = try {
                    HttpClient(CIO) {
                        install(HttpTimeout) {
                            requestTimeoutMillis = 5000
                        }
                    }.get(address, block = {
                        parameter("json", json.toString(1))
                    })
                } catch (ex: Exception) { logger.warn("Request failed with exception: ${ex.message}"); null }

                // when request completes, call the on complete function, using try catch in case json conversion fails
                val text = response?.bodyAsText() ?: "{}"
                val result = try {
                    JSONObject(text).toResult()
                } catch (ex: JSONException) {
                    val error = "Invalid input json received: $text"
                    logger.error(error)
                    Result.Error(error)
                }
                future.complete(result)
            }
        }

        return future
    }
}