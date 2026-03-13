package eu.europa.ec.commonfeature.util


object TestTag {

    object PinScreen {
        const val TITLE = "pin_screen_title"
        const val BUTTON = "pin_screen_button"
    }

    object SuccessScreen {
        const val PRIMARY_BUTTON = "success_screen_primary_button"
        const val SECONDARY_BUTTON = "success_screen_secondary_button"
    }

    object DocumentSuccessScreen {
        const val CONTENT_HEADER_DESCRIPTION = "document_success_screen_content_header_description"
        const val BUTTON = "document_success_screen_button"

        fun successDocument(index: Int) = "document_success_screen_document_$index"
    }

    object RequestScreen {
        const val CONTENT_HEADER_DESCRIPTION = "request_screen_content_header_description"
        const val BUTTON = "request_screen_button"

        fun requestedDocument(index: Int) = "request_screen_requested_document_$index"
    }

    object BiometricScreen {
        const val PIN_TEXT = "biometric_screen_pin_text"
        const val PIN_TITLE = "biometric_screen_title"
    }
}