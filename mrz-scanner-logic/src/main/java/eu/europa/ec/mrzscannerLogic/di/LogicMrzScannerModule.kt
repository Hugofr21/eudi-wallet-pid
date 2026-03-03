package eu.europa.ec.mrzscannerLogic.di

import android.hardware.SensorManager
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.mrzscannerLogic.controller.LivenessDetectionFaceController
import eu.europa.ec.mrzscannerLogic.controller.LivenessDetectionFaceControllerImpl
import eu.europa.ec.mrzscannerLogic.controller.MrzScanController
import eu.europa.ec.mrzscannerLogic.controller.MrzScanControllerImpl
import eu.europa.ec.mrzscannerLogic.service.AnalyzerGuidelineCardService
import eu.europa.ec.mrzscannerLogic.service.AnalyzerGuidelineCardServiceImpl
import eu.europa.ec.mrzscannerLogic.service.CameraFrontService
import eu.europa.ec.mrzscannerLogic.service.CameraFrontServiceImpl
import eu.europa.ec.mrzscannerLogic.service.CameraService
import eu.europa.ec.mrzscannerLogic.service.CameraServiceImpl
import eu.europa.ec.mrzscannerLogic.service.ChecksumValidationService
import eu.europa.ec.mrzscannerLogic.service.ChecksumValidationServiceImpl
import eu.europa.ec.mrzscannerLogic.service.DriverLicenseParseService
import eu.europa.ec.mrzscannerLogic.service.DriverLicenseParseServiceImpl
import eu.europa.ec.mrzscannerLogic.service.FaceService
import eu.europa.ec.mrzscannerLogic.service.FaceServiceImpl
import eu.europa.ec.mrzscannerLogic.service.MrzParserService
import eu.europa.ec.mrzscannerLogic.service.MrzParserServiceImpl
import eu.europa.ec.mrzscannerLogic.service.OcrCorrectionService
import eu.europa.ec.mrzscannerLogic.service.OcrCorrectionServiceImpl
import eu.europa.ec.mrzscannerLogic.service.SensorDocumentService
import eu.europa.ec.mrzscannerLogic.service.SensorDocumentServiceImpl
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
fun provideCameraService(
    resourceProvider: ResourceProvider,
): CameraService =
    CameraServiceImpl(resourceProvider)

@Single
fun provideCameraFrontService(
    resourceProvider: ResourceProvider,
    logController: LogController
): CameraFrontService =
    CameraFrontServiceImpl(resourceProvider, logController)



@Single
fun provideOcrCorrectionService(): OcrCorrectionService =
    OcrCorrectionServiceImpl()

@Single
fun provideChecksumValidationService(): ChecksumValidationService =
    ChecksumValidationServiceImpl()

@Single
fun provideMrzParserService(
    checksumService: ChecksumValidationService,
    correctionService: OcrCorrectionService
): MrzParserService =
    MrzParserServiceImpl(
        checksumService = checksumService,
        correctionService = correctionService
    )

@Single
fun provideTextRecognitionService(): TextRecognitionService =
    TextRecognitionServiceImpl()


@Single
fun provideDriverLicenseParseService(): DriverLicenseParseService =
    DriverLicenseParseServiceImpl()


@Single
fun provideFaceService(): FaceService =
    FaceServiceImpl()


@Single
fun provideAnalyzerGuidelineCardService(
    log: LogController
): AnalyzerGuidelineCardService =
    AnalyzerGuidelineCardServiceImpl(log = log)

@Single
fun providerSensorDocumentServiceImpl(
    log: LogController,
    resourceProvider: ResourceProvider
): SensorDocumentService = SensorDocumentServiceImpl(log, resourceProvider)

@Single
fun provideMrzScanController(
    cameraService: CameraService,
    resourceProvider: ResourceProvider,
    parserService: MrzParserService,
    sensorDocumentService: SensorDocumentService,
    analyzerGuidelineCardService: AnalyzerGuidelineCardService,
    faceService: FaceService,
    driverLicenseParseService : DriverLicenseParseService,
    textRecognitionService: TextRecognitionService,
): MrzScanController =
    MrzScanControllerImpl(
        cameraService = cameraService,
        resourceProvider = resourceProvider,
        parserService = parserService,
        faceService = faceService,
        driverLicenseParseService = driverLicenseParseService,
        textRecognitionService = textRecognitionService,
        sensorDocumentService = sensorDocumentService,
        analyzerGuidelineCardService = analyzerGuidelineCardService
    )


@Single
fun provideLivenessDetectionsFaceController(
    cameraFrontService: CameraFrontService,
    logController: LogController
): LivenessDetectionFaceController =
    LivenessDetectionFaceControllerImpl(
        cameraFrontService = cameraFrontService,
        logController = logController,
    )
