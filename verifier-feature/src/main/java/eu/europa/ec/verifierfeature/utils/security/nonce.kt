package eu.europa.ec.verifierfeature.utils.security

import eu.europa.ec.verifierfeature.controller.document.VerifyNonceResult

fun verifyNonce(expected: String, actual: String): VerifyNonceResult =
    if (expected == actual) {
        VerifyNonceResult.Success(actual)
    } else {
        VerifyNonceResult.Failure(expected = expected, actual = actual)
    }