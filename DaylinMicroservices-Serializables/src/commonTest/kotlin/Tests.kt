import daylightnebula.daylinmicroservices.serializables.Result
import daylightnebula.daylinmicroservices.serializables.Schema
import daylightnebula.daylinmicroservices.serializables.SchemaElement
import daylightnebula.daylinmicroservices.serializables.validate
import org.json.JSONObject
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

val testValidObject = JSONObject()
    .put("name", "Bobby")
    .put("lastTimes", listOf<Number>())
    .put("position", arrayOf(32, 32, 32))
    .put("info", JSONObject()
        .put("age", 32)
        .put("image", "asfdabwer".toByteArray())
        .put("enabled", false)
    )


val testInvalidObject = JSONObject()
    .put("name", "Bobby")

val testDefaultSchema = Schema(
    "name" to SchemaElement.String(),
    "age" to SchemaElement.Default(SchemaElement.Number(), -1)
)

val testDefaultObject1 = JSONObject()
    .put("name", "Bobby")

val testDefaultObject2 = JSONObject()
    .put("name", "Joe")
    .put("age", 30)

val testDefaultObject3 = JSONObject()
    .put("name", "Michelle")
    .put("age", "abc")

val testOptionalSchema = Schema(
    "name" to SchemaElement.String(),
    "age" to SchemaElement.Optional(SchemaElement.Number())
)

val testOption1 = JSONObject()
    .put("name", "Bobby")

val testOption2 = JSONObject()
    .put("name", "Joe")
    .put("age", 30)

class Tests {
    @Test
    fun testTrue() {
        assertTrue {
            val result = testValidObject.validate(testSchema)

            if (result.isError()) println("ERROR: ${result.error()}")
            result.isOk()
        }
    }

    @Test
    fun testInvalid() {
        assertFalse {
            val result = testInvalidObject.validate(testSchema)

            result.isOk()
        }
    }

    @Test
    fun testDefaults1() = assertTrue {
        val result = testDefaultObject1.validate(testDefaultSchema)

        if (result.isOk()) {
            val json = result.unwrap()
            if (json.has("age"))
                json.optInt("age", 0) == -1
            else false
        } else false
    }

    @Test
    fun testDefaults2() = assertTrue {
        val result = testDefaultObject2.validate(testDefaultSchema)

        if (result.isOk()) {
            val json = result.unwrap()
            if (json.has("age"))
                json.optInt("age", 0) == 30
            else false
        } else false
    }

    @Test
    fun testDefaults3() = assertFalse {
        val result = testDefaultObject3.validate(testDefaultSchema)

        if (result.isOk()) {
            val json = result.unwrap() as JSONObject
            if (json.has("age"))
                json.optInt("age", 0) == -1
            else false
        } else false
    }

    @Test
    fun testOption1() = assertTrue {
        val result = testOption1.validate(testOptionalSchema)

        if (result.isOk()) {
            val json = result.unwrap()
            (!json.has("age"))
        } else false
    }

    @Test
    fun testOption2() = assertTrue {
        val result = testOption2.validate(testOptionalSchema)

        if (result.isOk()) {
            val json = result.unwrap()
            if (json.has("age"))
                json.optInt("age", 0) == 30
            else false
        } else false
    }
}