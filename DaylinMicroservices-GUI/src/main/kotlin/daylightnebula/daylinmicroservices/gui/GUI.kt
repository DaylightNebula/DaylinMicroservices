package daylightnebula.daylinmicroservices.gui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import daylightnebula.daylinmicroservices.Microservice
import daylightnebula.daylinmicroservices.MicroserviceConfig
import daylightnebula.daylinmicroservices.gui.panels.SidePanel
import kotlin.concurrent.thread

// create service
val service = Microservice(
    MicroserviceConfig(
        "gui", "gui",
        listOf("gui")
    ),
    endpoints = hashMapOf()
)

@Composable
fun render() {
    val count = remember { mutableStateOf(0) }
    Row(Modifier.fillMaxSize().background(Colors.Grays.DarkGray)) {
        Column(
            Modifier.weight(1f).fillMaxHeight()
        ) {
            SidePanel.drawFull()
        }
        Column(
            Modifier.weight(4f).fillMaxHeight()
        ) {
            Text(text = "Weight = 2")
        }
    }
}

fun stop() {
    // run dispose on another thread so that shutdown does not lag
    thread { service.dispose() }
}

fun main() = application {

    // on close, call stop then close the window
    val onClose: () -> Unit = {
        stop()
        exitApplication()
    }

    // create window
    Window(
        onCloseRequest = onClose,
        title = "Daylin Microservices GUI",
        state = rememberWindowState(width = 1280.dp, height = 720.dp),
    ) {
        render()
    }

    service.start()
}