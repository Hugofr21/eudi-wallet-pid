package eu.europa.ec.mrzscannerLogic.di

import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import eu.europa.ec.mrzscannerLogic.controller.MrzScanController
import eu.europa.ec.mrzscannerLogic.controller.MrzScanControllerImpl
import eu.europa.ec.mrzscannerLogic.service.ChecksumValidationService
import eu.europa.ec.mrzscannerLogic.service.ChecksumValidationServiceImpl
import eu.europa.ec.mrzscannerLogic.service.MrzParserService
import eu.europa.ec.mrzscannerLogic.service.MrzParserServiceImpl
import eu.europa.ec.mrzscannerLogic.service.OcrCorrectionService
import eu.europa.ec.mrzscannerLogic.service.OcrCorrectionServiceImpl
import eu.europa.ec.mrzscannerLogic.service.TextRecognitionService
import eu.europa.ec.mrzscannerLogic.service.TextRecognitionServiceImpl
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single


@Module
@ComponentScan("eu.europa.ec.mrzscannerLogic")
class LogicMrzScannerModule

@Single
fun provideOcrCorrectionService(): OcrCorrectionService = OcrCorrectionServiceImpl()

@Single
fun provideChecksumValidationService(): ChecksumValidationService = ChecksumValidationServiceImpl()

@Single
fun provideMrzParserService(
     checksumService: ChecksumValidationService,
     correctionService: OcrCorrectionService
): MrzParserService = MrzParserServiceImpl(
    checksumService = checksumService,
    correctionService = correctionService
)



@Single
fun provideTextRecognitionService(): TextRecognitionService = TextRecognitionServiceImpl()



@Single
fun provideMrzScanController(
    resourceProvider: ResourceProvider,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    parserService: MrzParserService,
    textRecognitionService: TextRecognitionService
): MrzScanController =
    MrzScanControllerImpl(
        resourceProvider = resourceProvider,
        lifecycleOwner =lifecycleOwner,
        previewView = previewView,
        parserService = parserService,
        textRecognitionService = textRecognitionService

    )

