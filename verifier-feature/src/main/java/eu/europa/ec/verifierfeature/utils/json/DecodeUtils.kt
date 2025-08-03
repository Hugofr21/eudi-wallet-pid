package eu.europa.ec.verifierfeature.utils.json

import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.util.Base64URL
import kotlin.collections.component1
import kotlin.collections.component2

object DecodeUtils {
    fun decodeJwt(jwt: String) {

        val parts = jwt.split(".")
        if (parts.size != 3) {
            throw IllegalArgumentException("Malformed JWT: Expected 3 parts, arrived ${parts.size}")
        }

        val headerJson  = String(Base64URL(parts[0]).decode(), Charsets.UTF_8)
        val payloadJson = String(Base64URL(parts[1]).decode(), Charsets.UTF_8)

        println("=== HEADER ===")
        println(headerJson)
        println()

        println("=== PAYLOAD ===")
        println(payloadJson)
        println()

        val jwsObj = JWSObject.parse(jwt)
        val header  = jwsObj.header.toJSONObject()
        val claims  = jwsObj.payload.toJSONObject()

        println("=== HEADER (as Map) ===")
        header.forEach { (k, v) -> println("$k: $v") }
        println()

        println("=== CLAIMS (as Map) ===")
        claims.forEach { (k, v) -> println("$k: $v") }
    }

}