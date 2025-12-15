package eu.europa.ec.mrzscannerLogic.model

sealed class ScanType {
    object Face : ScanType()
    object Document : ScanType()
}