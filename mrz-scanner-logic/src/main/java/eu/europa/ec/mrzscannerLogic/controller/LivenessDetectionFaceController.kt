package eu.europa.ec.mrzscannerLogic.controller

import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.mrzscannerLogic.service.CameraFrontService

interface LivenessDetectionFaceController{

}

class LivenessDetectionFaceControllerImpl(
    private val cameraFrontService: CameraFrontService,
    private val logController: LogController
): LivenessDetectionFaceController{

}