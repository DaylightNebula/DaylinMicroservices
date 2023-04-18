package daylightnebula.daylinmicroservices.gui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import daylightnebula.daylinmicroservices.gui.Colors

abstract class Panel(val backgroundColor: Color) {
    @Composable
    fun drawFull() {
        Column(
            Modifier.fillMaxSize().background(backgroundColor)
        ) {
            draw()
        }
    }

    @Composable
    abstract fun draw()
}