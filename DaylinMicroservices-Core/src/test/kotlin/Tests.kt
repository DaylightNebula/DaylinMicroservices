import daylightnebula.daylinmicroservices.core.Microservice
import daylightnebula.daylinmicroservices.core.MicroserviceConfig
import daylightnebula.daylinmicroservices.core.requests.requestByName
import daylightnebula.daylinmicroservices.serializables.Schema
import daylightnebula.daylinmicroservices.serializables.SchemaElement
import daylightnebula.daylinmicroservices.serializables.Result
import org.json.JSONObject
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.lang.Thread.sleep
import kotlin.test.assertFalse
import kotlin.test.assertTrue

val svc1Config = MicroserviceConfig(
    name = "svc1",
    registerUpdateInterval = "10s"
)

val svc2Config = MicroserviceConfig(
    name = "svc2",
    registerUpdateInterval = "10s"
)

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class Tests {
    @Test
    fun testInfo() {
        val (svc1, svc2) = startTestServices()

        assertTrue {
            svc1.services.size > 0
        }

        assertTrue {
            val request = svc1.requestByName("svc2", "test", JSONObject().put("info", "hi"))?.get()
            println("Request: $request")
            if (request != null && request.isOk()) {
                val response = request.unwrap()
                println("RESPONSE: " + response.toString(4))
                response.getString("info") == "hi"
            } else {
                if (request != null) println("Request failed with error ${request.error()}")
                false
            }
        }

        svc1.dispose()
        svc2.dispose()
    }

    @Test
    fun testFailInfo() {
        val (svc1, svc2) = startTestServices()

        assertTrue  {
            val request = svc1.requestByName("svc2", "test", JSONObject())
            if (request != null) {
                val response = request.get()
                println("RESPONSE: ${response.serialize().toString(0)}")
                response.isError()
            } else false
        }

        svc1.dispose()
        svc2.dispose()
    }

    fun startTestServices(): Pair<Microservice, Microservice> {
        val svc1 = Microservice(
            svc1Config,
            hashMapOf(
                "test" to (Schema("info" to SchemaElement.String()) to {
                    Result.Ok(JSONObject().put("info", it.getString("info")!!))
                })
            )
        )

        val svc2 = Microservice(
            svc2Config,
            hashMapOf(
                "test" to (Schema("info" to SchemaElement.String()) to { obj ->
                    Result.Ok(JSONObject().put("info", obj.getString("info")!!))
                })
            )
        )

        svc1.start()
        svc2.start()

        sleep(5000)

        return svc1 to svc2
    }
}