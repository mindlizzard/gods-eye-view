package com.mindlizzard.godseye

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.maps3d.GoogleMap3D
import com.google.android.gms.maps3d.Map3DInitConfig
import com.google.android.gms.maps3d.Map3DView
import com.google.android.gms.maps3d.OnMap3DViewReadyCallback
import com.google.android.gms.maps3d.model.Map3DMode
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GodsEyeScreen()
                }
            }
        }
    }
}

@Composable
private fun GodsEyeScreen() {
    var mapReady by remember { mutableStateOf(false) }
    var mapError by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Map3DHost(
            modifier = Modifier.fillMaxSize(),
            onReady = {
                mapReady = true
                mapError = null
            },
            onError = { error ->
                mapReady = false
                mapError = error.message ?: error.javaClass.simpleName
            }
        )

        Card(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = 16.dp, vertical = 48.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.72f)
            ),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = "GOD'S EYE",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = if (mapReady) "Native 3D engine online" else "Native 3D engine starting…",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .background(
                    Color.Black.copy(alpha = 0.72f),
                    RoundedCornerShape(18.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("3D")
            Text("LAYERS")
            Text("UAP")
            Text("SAT")
        }

        mapError?.let { message ->
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "3D-kaart kon niet starten",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun Map3DHost(
    modifier: Modifier = Modifier,
    onReady: (GoogleMap3D) -> Unit,
    onError: (Exception) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnReady = rememberUpdatedState(onReady)
    val currentOnError = rememberUpdatedState(onError)

    val map3DView = remember {
        val config = Map3DInitConfig.create(
            centerLat = 52.1,
            centerLng = 5.3,
            centerAlt = 0.0,
            heading = 0.0,
            tilt = 55.0,
            roll = 0.0,
            range = 2_000_000.0,
            minAltitude = 0.0,
            maxAltitude = 10_000_000.0,
            minHeading = 0.0,
            maxHeading = 360.0,
            minTilt = 0.0,
            maxTilt = 90.0,
            bounds = null,
            mapMode = Map3DMode.HYBRID,
            mapId = null,
            language = Locale.getDefault().language,
            region = Locale.getDefault().country
        )

        Map3DView(context, config).apply {
            onCreate(null)
            getMap3DViewAsync(object : OnMap3DViewReadyCallback {
                override fun onMap3DViewReady(googleMap3D: GoogleMap3D) {
                    currentOnReady.value(googleMap3D)
                }

                override fun onError(error: Exception) {
                    currentOnError.value(error)
                }
            })
        }
    }

    DisposableEffect(lifecycleOwner, map3DView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> map3DView.onStart()
                Lifecycle.Event.ON_RESUME -> map3DView.onResume()
                Lifecycle.Event.ON_PAUSE -> map3DView.onPause()
                Lifecycle.Event.ON_STOP -> map3DView.onStop()
                Lifecycle.Event.ON_DESTROY -> map3DView.onDestroy()
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { map3DView }
    )
}
