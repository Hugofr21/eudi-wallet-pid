package eu.europa.ec.mrzscannerLogic.di

import android.content.Context
import eu.europa.ec.mrzscannerLogic.controller.MrzScanController
import eu.europa.ec.mrzscannerLogic.controller.MrzScanControllerImpl
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single


@Module
@ComponentScan("eu.europa.ec.mrzscannerLogic")
class LogicMrzScannerModule

@Single
fun provideMrzScanController(
    context: Context
): MrzScanController =
    MrzScanControllerImpl()

