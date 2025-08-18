package eu.europa.ec.verifierfeature.model

/**
 * Qualified Website Authentication Certificate
 * Trusted List (TL)  ETSI TS 119 612,
 */
data class Verifier(
    val id: String,
    val name: String,
    val url: String,
    val schema: String? = null,
    val description: String? = null,
    val recommended: Boolean = false,
    var isSelected: Boolean = false
)

object VerifierModule {
    fun trustListVerifier(): List<Verifier> {
        return listOf(
            Verifier(
                id = "eudiw",
                name = "Eu Digital Wallet Identity",
                url = "https://verifier.eudiw.dev/home",
                schema = "eudi-openid4vp://",
                description = "OpenID4VP demo verifier",
                recommended = true
            ),
            Verifier(
                id = "eudiw_other",
                name = "Verifier for other document categories",
                url = "https://verifier.eudiw.dev/home",
                schema = "eudi-openid4vp://",
                description = "OpenID4VP demo verifier",
                recommended = true
            ),
            Verifier(
                id = "age",
                name = "Age Verification Testing Verifier",
                url = "https://verifier.ageverification.dev/",
                schema = "av://",
                description = "Teste de verificação de idade"
            ),
            Verifier(
                id = "test_rp",
                name = "Test Relying Party",
                url = "https://tester.relyingparty.eudiw.dev/",
                schema = "eudi-openid4vp://",
                description = "Relying party de teste para fluxo EUDIW"
            ),
            Verifier(
                id = "proof_age",
                name = "Proof Age Relying Party",
                url = "https://verifier.ageverification.dev/",
                schema = "av://",
                description = "Relying party de teste para fluxo EUDIW"
            )
        )
    }

    fun optionsVerifierName(): List<String> = trustListVerifier().map { it.name }
}
