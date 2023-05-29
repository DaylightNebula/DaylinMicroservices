package daylightnebula.daylinmicroservices.serializables

object Utils {
    val base64Regex = Regex.fromLiteral("^([A-Za-z0-9+/]{4})*([A-Za-z0-9+/]{3}=|[A-Za-z0-9+/]{2}==)?\$")
}