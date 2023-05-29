import daylightnebula.daylinmicroservices.serializables.DynamicObject
import daylightnebula.daylinmicroservices.serializables.Schema
import daylightnebula.daylinmicroservices.serializables.SchemaElement
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

val testSchema = Schema(
    "name" to SchemaElement.String(),
    "lastTimes" to SchemaElement.List(SchemaElement.Number()),
    "position" to SchemaElement.Array(SchemaElement.Number(), 3),
    "info" to SchemaElement.Object(Schema(
        "age" to SchemaElement.Number(),
        "image" to SchemaElement.Data(),
        "enabled" to SchemaElement.Boolean(),
    ))
)

val testValidObject = DynamicObject()
    .put("name", "Bobby")
    .put("lastTimes", listOf<Number>())
    .put("position", arrayOf(32, 32, 32))
    .put("info", DynamicObject()
        .put("age", 32)
        .put("image", "asfdabwer".toByteArray())
        .put("enabled", false)
    )


val testInvalidObject = DynamicObject()
    .put("name", "Bobby")
//    .put("lastTimes", listOf<Number>())
//    .put("position", arrayOf(32, 32, 32))
//    .put("info", DynamicObject(testSchema, "info")
//        .put("age", 32)
//        .put("image", "asfdabwer".toByteArray())
//        .put("enabled", false)
//    )

class Tests {
    @Test
    fun testTrue() {
        assertTrue {
            val result = testValidObject.validateToResult(testSchema)
            if (result.isOk())
                testSchema.validate(result.unwrap() as JsonObject)
            else false
        }
    }

    @Test
    fun testInvalid() {
        assertFalse {
            val result = testInvalidObject.validateToResult(testSchema)
            if (result.isOk())
                testSchema.validate(result.unwrap() as JsonObject)
            else false
        }
    }
}