package eu.europa.ec.verifierfeature.utils


import com.nimbusds.jose.util.JSONObjectUtils
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer

internal val jsonSupport = Json {
    prettyPrint = false
    ignoreUnknownKeys = true
}

internal inline fun <reified T> JsonElement.decodeAs(deserializer: DeserializationStrategy<T> = serializer()): Result<T> =
    runCatching {
        jsonSupport.decodeFromJsonElement(deserializer, this)
    }

internal fun Map<String, Any?>.toJsonObject(): JsonObject {
    val jsonString = JSONObjectUtils.toJSONString(this)
    return jsonSupport.decodeFromString(jsonString)
}