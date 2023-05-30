package daylightnebula.daylinmicroservices.core.requests

import daylightnebula.daylinmicroservices.serializables.DynamicObject
import daylightnebula.daylinmicroservices.serializables.Result
import daylightnebula.daylinmicroservices.serializables.Schema
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread

internal object Requester {
    fun rawRequest(logger: Logger, address: String, schema: Schema, json: DynamicObject): CompletableFuture<Result<DynamicObject>> {
        val future = CompletableFuture<Result<DynamicObject>>()
        val result = json.validateToResult(schema)
        if (result.isError()) {
            future.complete(Result.Error(result.error()))
        } else {
            val json = result.unwrap()
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
                            parameter("json", Json.encodeToString(json))
                        })
                    } catch (ex: Exception) {
                        logger.warn("Request failed with exception: ${ex.message}"); null
                    }

                    // when request completes, call the on complete function
                    future.complete(Json.decodeFromString<Result<DynamicObject>>(response?.bodyAsText() ?: "{}"))
                }
            }
        }

        return future
    }
}