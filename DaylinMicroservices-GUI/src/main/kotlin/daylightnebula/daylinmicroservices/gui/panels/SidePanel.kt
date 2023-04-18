package daylightnebula.daylinmicroservices.gui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import daylightnebula.daylinmicroservices.gui.Colors

object SidePanel: Panel(backgroundColor = Colors.Primaries.Green) {
    @Composable
    override fun draw() {
        Button(
            onClick = { println("TODO Home click") },
            modifier = Modifier.padding(10.dp).fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Home", color = Colors.Grays.Offwhite)
            }
        }
    }
}