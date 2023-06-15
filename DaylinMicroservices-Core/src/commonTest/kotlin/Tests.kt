import daylightnebula.daylinmicroservices.core.Microservice
import daylightnebula.daylinmicroservices.core.MicroserviceConfig
import org.junit.Test

//val svc1Config = MicroserviceConfig(
//    "svc1",
//    "svc1",
//    listOf()
//)
//
//val svc2Config = MicroserviceConfig(
//    "svc2",
//    "svc2",
//    listOf()
//)
//
//@Test
//fun testInfo() {
//    val svc1 = Microservice(
//        svc1Config,
//        hashMapOf(
//            "test" to (Schema("info" to SchemaElement.String()) to { obj ->
//                Result.Ok(DynamicObject().put("info", obj.getString("info")!!))
//            })
//        )
//    )
//
//    val svc2 = Microservice(
//        svc2Config,
//        hashMapOf(
//            "test" to (Schema("info" to SchemaElement.String()) to { obj ->
//                Result.Ok(DynamicObject().put("info", obj.getString("info")!!))
//            })
//        )
//    )
//
//    svc1.start()
//    svc2.start()
//}