package daylightnebula.daylinmicroservices.gui.panels

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.orbitz.consul.model.health.Service
import compose.icons.Octicons
import compose.icons.octicons.HomeFill24
import daylightnebula.daylinmicroservices.gui.Colors
import daylightnebula.daylinmicroservices.gui.service
import kotlinx.coroutines.delay

object SidePanel: Panel(backgroundColor = Colors.Grays.Offwhite) {
    @Composable
    override fun draw() {
        var mapHashCode by remember { mutableStateOf(service.getServices().hashCode()) }
        LaunchedEffect(service.getServices()) {
            while (true) {
                val newHashCode = service.getServices().hashCode()
                println("Checking? ${newHashCode == mapHashCode}")
                if (newHashCode != mapHashCode) {
                    mapHashCode = newHashCode
                }
                delay(1000L)
            }
        }

        // add top elements
        header()
        homeButton()

        // split everything up
        Divider(thickness = 1.dp, color = Colors.Grays.DarkGray, modifier = Modifier.padding(start = 10.dp, end = 10.dp))

        // list the available services
        println("Attempt to render services: ${service.getServices().size}")
        service.getServices().values.forEach { serviceButton(it) }
    }
}

// draw the header for the sidebar
@Composable
fun header() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "DaylinMicroservices GUI",
            textAlign = TextAlign.Center,
            fontFamily = FontFamily.SansSerif,
            fontSize = 1.em,
            color = Colors.Grays.DarkGray
        )
    }
}

// draw the home button for the sidebar
@Composable
fun homeButton() {
    Button(
        onClick = { println("TODO Home click") },
        modifier = Modifier.padding(10.dp).fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(backgroundColor = Colors.Primaries.Green),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Octicons.HomeFill24,
                contentDescription = "Home",
                modifier = Modifier.height(20.dp),
                tint = Colors.Grays.Offwhite
            )
            Spacer(Modifier.width(5.dp))
            Text("Home", color = Colors.Grays.Offwhite, fontFamily = FontFamily.SansSerif, fontSize = 1.em)
        }
    }
}

@Composable
fun serviceButton(service: Service) {
    println("Rendering ${service.service}")
    Button(
        onClick = { println("TODO Home click") },
        modifier = Modifier.padding(10.dp).fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(backgroundColor = Colors.Primaries.Green),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = service.service,
                color = Colors.Grays.Offwhite,
                fontFamily = FontFamily.SansSerif,
                fontSize = 1.em
            )
        }
    }
}