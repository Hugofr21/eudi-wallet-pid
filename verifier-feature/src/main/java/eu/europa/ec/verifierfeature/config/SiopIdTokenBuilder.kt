package eu.europa.ec.verifierfeature.config

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.ThumbprintUtils
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWT
import com.nimbusds.jwt.SignedJWT
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import com.nimbusds.oauth2.sdk.id.Audience
import com.nimbusds.oauth2.sdk.id.Issuer
import com.nimbusds.oauth2.sdk.id.Subject
import com.nimbusds.openid.connect.sdk.claims.IDTokenClaimsSet
import eu.europa.ec.eudi.openid4vp.ResolvedRequestObject
import java.io.Serializable
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.*

object SiopIdTokenBuilder {

    fun decodeAndVerify(jwt: String, walletPubKey: RSAKey): Result<IDTokenClaimsSet> = runCatching {
        val jwtProcessor = DefaultJWTProcessor<SecurityContext>().also {
            it.jwsTypeVerifier = DefaultJOSEObjectTypeVerifier(JOSEObjectType.JWT)
            val jwsAlg = JWSAlgorithm.RS256
            val jwkSet: JWKSource<SecurityContext> = ImmutableJWKSet(JWKSet(walletPubKey))
            it.jwsKeySelector = JWSVerificationKeySelector(
                jwsAlg,
                jwkSet,
            )
        }
        val claimsSet = jwtProcessor.process(jwt, null)
        IDTokenClaimsSet(claimsSet)
    }

    fun randomKey(): RSAKey = RSAKeyGenerator(2048)
        .keyUse(KeyUse.SIGNATURE)
        .keyID(UUID.randomUUID().toString())
        .issueTime(Date(System.currentTimeMillis()))
        .generate()

    fun build(
        request: ResolvedRequestObject.SiopAuthentication,
        rsaJWK: RSAKey,
        clock: Clock = Clock.systemDefaultZone(),
    ): String {
        fun sign(claimSet: IDTokenClaimsSet): Result<JWT> = runCatching {
            val header = JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(rsaJWK.keyID)
                .type(JOSEObjectType.JWT)
                .build()
            val signedJWT = SignedJWT(header, claimSet.toJWTClaimsSet())
            signedJWT.sign(RSASSASigner(rsaJWK))
            signedJWT
        }

        fun buildJWKThumbprint(): String = ThumbprintUtils.compute("SHA-256", rsaJWK).toString()

        fun computeTokenDates(clock: Clock): Pair<Date, Date> {
            val iat = clock.instant()
            val exp = iat.plusMillis(Duration.ofMinutes(10).toMillis())
            fun Instant.toDate() = Date.from(atZone(ZoneId.systemDefault()).toInstant())
            return iat.toDate() to exp.toDate()
        }

        val (iat, exp) = computeTokenDates(clock)

        return with(
            IDTokenClaimsSet(
                Issuer(buildJWKThumbprint()),
                Subject(buildJWKThumbprint()),
                listOf(Audience(request.client.id.toString())),
                exp,
                iat,
            ),
        ) {
            subjectJWK = rsaJWK.toPublicJWK()

            sign(this).getOrThrow().serialize()
        }
    }
}