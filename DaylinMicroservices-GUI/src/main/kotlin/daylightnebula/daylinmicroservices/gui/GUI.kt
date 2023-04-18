package daylightnebula.daylinmicroservices.gui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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

@Composable
fun render() {
    val count = remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize(), Arrangement.spacedBy(5.dp)) {
        Button(modifier = Modifier.align(Alignment.CenterHorizontally),
            onClick = {
                count.value++
            }, colors = ButtonDefaults.buttonColors(
                backgroundColor = Colors.Grays.darkgray
            )) {
            Text(if (count.value == 0) "Hello World" else "Clicked ${count.value}!")
        }
        Button(modifier = Modifier.align(Alignment.CenterHorizontally),
            onClick = {
                count.value = 0
            }) {
            Text("Reset")
        }
    }
}

fun stop() {

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
        title = "Spider Monkey Editor",
        state = rememberWindowState(width = 1280.dp, height = 720.dp),
    ) {
        render()
    }
}