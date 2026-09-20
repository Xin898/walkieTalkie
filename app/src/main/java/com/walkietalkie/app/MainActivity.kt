package com.walkietalkie.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { WalkieScreen() } }
}

@Composable
private fun WalkieScreen(model: WalkieViewModel = viewModel()) {
    val state by model.state.collectAsState()
    LaunchedEffect(Unit) { model.connect("android-user") }
    DisposableEffect(Unit) { onDispose { model.stopTalking() } }
    val status = when (state.connection) { ConnectionState.Connected -> if (state.speaking) "Transmitting" else "Listening"; ConnectionState.Connecting -> "Connecting"; ConnectionState.Disconnected -> "Disconnected"; is ConnectionState.Error -> "Reconnecting" }
    MaterialTheme {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("Channel ${state.channel}", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            Text(status)
            Spacer(Modifier.height(36.dp))
            Button(onClick = {}, modifier = Modifier.size(220.dp).pointerInput(Unit) { detectTapGestures(onPress = { model.startTalking(); tryAwaitRelease(); model.stopTalking() }) }) { Text(if (state.speaking) "TALKING" else "HOLD TO TALK") }
        }
    }
}
