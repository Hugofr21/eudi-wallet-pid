package eu.europa.ec.issuancefeature.utils.codePlaceholder

object TestTag {

    object AddDocumentScreen {
        const val SUBTITLE = "add_document_screen_subtitle"
        fun optionItem(issuerId: String, configIds: List<String>) =
            "add_document_screen_attestation_${issuerId}_${configIds.joinToString(",")}"
    }

    object DocumentOfferScreen {
        const val CONTENT_HEADER_DESCRIPTION = "document_offer_screen_content_header_description"
        const val BUTTON = "document_offer_screen_button"
    }
}