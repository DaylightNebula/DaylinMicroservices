import daylightnebula.daylinmicroservices.serializables.DynamicObject
import daylightnebula.daylinmicroservices.serializables.Schema
import daylightnebula.daylinmicroservices.serializables.SchemaElement
import kotlin.test.Test
import kotlin.test.assertFails
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

val testValidObject = DynamicObject(testSchema)
    .put("name", "Bobby")
    .put("lastTimes", listOf<Number>())
    .put("position", arrayOf(32, 32, 32))
    .put("info", DynamicObject(testSchema, "info")
        .put("age", 32)
        .put("image", "asfdabwer".toByteArray())
        .put("enabled", false)
    )


val testInvalidObject = DynamicObject(testSchema)
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
        assertTrue { testSchema.validate(testValidObject.getOutput()) }
    }

    @Test
    fun testInvalid() {
        assertFails { testSchema.validate(testInvalidObject.getOutput()) }
    }
}