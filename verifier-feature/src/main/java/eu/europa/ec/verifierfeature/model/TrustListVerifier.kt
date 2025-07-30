package eu.europa.ec.verifierfeature.model

/**
 * Qualified Website Authentication Certificate
 * Trusted List (TL)  ETSI TS 119 612,
 */
data class Verifier(
    val name: String,
    val url: String,
    val schema:String? = null,
)

object VerifierModule{
    fun trustListVerifier(): List<Verifier>{
       return listOf(   Verifier("Eu Digital Wallet Identity", "https://verifier.eudiw.dev/home", "eudi-openid4vp"),
            Verifier("Age Verification Testing Verifier", "https://verifier.ageverification.dev/", "av"),
            Verifier("Funke Testbed", "https://testbed.lissi.io/funke", "haip"),
            Verifier("EUDI Verifier Endpoint OpenId4VP draft 23", "http://localhost:8080/swagger-ui#","")
           )
    }

    fun optionsVerifierName(): List<String>{
        return trustListVerifier().map {
            it.name
        }
    }
}