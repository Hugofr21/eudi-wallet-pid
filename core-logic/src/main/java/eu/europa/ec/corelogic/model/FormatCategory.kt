package eu.europa.ec.corelogic.model

import eu.europa.ec.eudi.wallet.transfer.openId4vp.Format
import org.multipaz.crypto.Algorithm

// OpenID for Verifiable Presentations 1.0
// SD-JWT-based Verifiable Credentials (SD-JWT LD)

sealed interface FormatCategory {
    data class SdJwtVcld(
        val sdJwtAlgorithms: List<Algorithm>,
        val jwsAlgorithms: List<Algorithm>,
    ) : FormatCategory {
        companion object {
            val ES_DEFAULTS = SdJwtVcld(
                sdJwtAlgorithms = listOf(
                    Algorithm.ES256,
                    Algorithm.ES384,
                    Algorithm.ES512
                ),
                jwsAlgorithms = listOf(
                    Algorithm.ES256,
                    Algorithm.ES384,
                    Algorithm.ES512
                )
            )
        }
    }

    // JSON‑LD + Linked Data Proofs
    data object JSON_LD_w3c : FormatCategory
}
