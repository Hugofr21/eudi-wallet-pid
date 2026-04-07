package eu.europa.ec.dashboardfeature.ui.scanner.utils

import eu.europa.ec.mrzscannerLogic.controller.Challenge

object Challenge {

     fun Challenge.toLabel(): String = when (this) {
        Challenge.LOOK_LEFT  -> "He looked to the left."
        Challenge.LOOK_RIGHT -> "He looked to the right."
        Challenge.BLINK      -> "He blinked."
        Challenge.SMILE      -> "Smiled"
        Challenge.OPEN_MOUTH -> "He opened his mouth."
        Challenge.NOD        -> "He nodded."
    }

     fun Challenge.toInstruction(): String = when (this) {
        Challenge.LOOK_LEFT  -> "He looked to the left."
        Challenge.LOOK_RIGHT -> "He looked to the right."
        Challenge.BLINK      -> "He blinked."
        Challenge.SMILE      -> "Smiled"
        Challenge.OPEN_MOUTH -> "He opened his mouth."
        Challenge.NOD        -> "He nodded."
    }
}