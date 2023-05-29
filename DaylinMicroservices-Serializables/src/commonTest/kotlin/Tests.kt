import daylightnebula.daylinmicroservices.serializables.DynamicObject
import daylightnebula.daylinmicroservices.serializables.Schema
import daylightnebula.daylinmicroservices.serializables.SchemaElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

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

val testDefaultSchema = Schema(
    "name" to SchemaElement.String(),
    "age" to SchemaElement.Default(SchemaElement.Number(), -1)
)

val testDefaultObject1 = DynamicObject()
    .put("name", "Bobby")

val testDefaultObject2 = DynamicObject()
    .put("name", "Joe")
    .put("age", 30)

val testDefaultObject3 = DynamicObject()
    .put("name", "Michelle")
    .put("age", "abc")

val testOptionalSchema = Schema(
    "name" to SchemaElement.String(),
    "age" to SchemaElement.Optional(SchemaElement.Number())
)

val testOption1 = DynamicObject()
    .put("name", "Bobby")

val testOption2 = DynamicObject()
    .put("name", "Joe")
    .put("age", 30)

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

    @Test
    fun testDefaults1() = assertTrue {
        val result = testDefaultObject1.validateToResult(testDefaultSchema)

        if (result.isOk()) {
            val json = result.unwrap() as JsonObject
            if (json.containsKey("age"))
                (json["age"]?.jsonPrimitive?.int ?: 0) == -1
            else false
        } else false
    }

    @Test
    fun testDefaults2() = assertTrue {
        val result = testDefaultObject2.validateToResult(testDefaultSchema)

        if (result.isOk()) {
            val json = result.unwrap() as JsonObject
            if (json.containsKey("age"))
                (json["age"]?.jsonPrimitive?.int ?: 0) == 30
            else false
        } else false
    }

    @Test
    fun testDefaults3() = assertTrue {
        val result = testDefaultObject3.validateToResult(testDefaultSchema)

        if (result.isOk()) {
            val json = result.unwrap() as JsonObject
            if (json.containsKey("age"))
                (json["age"]?.jsonPrimitive?.int ?: 0) == -1
            else false
        } else false
    }

    @Test
    fun testOption1() = assertTrue {
        val result = testOption1.validateToResult(testOptionalSchema)

        if (result.isOk()) {
            val json = result.unwrap() as JsonObject
            (!json.containsKey("age"))
        } else false
    }

    @Test
    fun testOption2() = assertTrue {
        val result = testOption2.validateToResult(testOptionalSchema)

        if (result.isOk()) {
            val json = result.unwrap() as JsonObject
            if (json.containsKey("age"))
                (json["age"]?.jsonPrimitive?.int ?: 0) == 30
            else false
        } else false
    }
}