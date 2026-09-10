package com.mindlizzard.godseye

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.maps3d.GoogleMap3D
import com.google.android.gms.maps3d.Map3DInitConfig
import com.google.android.gms.maps3d.Map3DView
import com.google.android.gms.maps3d.OnMap3DViewReadyCallback
import com.google.android.gms.maps3d.model.Map3DMode
import com.google.android.gms.maps3d.model.camera
import com.google.android.gms.maps3d.model.latLngAltitude
import java.util.Locale

class MainActivity : ComponentActivity(), OnMap3DViewReadyCallback {
    private lateinit var map3DView: Map3DView

    private val mapState = mutableStateOf<GoogleMap3D?>(null)
    private val mapReadyState = mutableStateOf(false)
    private val mapErrorState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        map3DView = Map3DView(this, createMapConfig())
        map3DView.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GodsEyeScreen(
                        map3DView = map3DView,
                        map3D = mapState.value,
                        mapReady = mapReadyState.value,
                        mapError = mapErrorState.value,
                    )
                }
            }
        }

        map3DView.getMap3DViewAsync(this)
    }

    override fun onMap3DViewReady(googleMap3D: GoogleMap3D) {
        mapState.value = googleMap3D
        mapReadyState.value = true
        mapErrorState.value = null
    }

    override fun onError(error: Exception) {
        mapReadyState.value = false
        mapErrorState.value = error.message ?: error.javaClass.simpleName
    }

    override fun onStart() {
        super.onStart()
        if (::map3DView.isInitialized) map3DView.onStart()
    }

    override fun onResume() {
        super.onResume()
        if (::map3DView.isInitialized) map3DView.onResume()
    }

    override fun onPause() {
        if (::map3DView.isInitialized) map3DView.onPause()
        super.onPause()
    }

    override fun onStop() {
        if (::map3DView.isInitialized) map3DView.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        mapState.value = null
        if (::map3DView.isInitialized) map3DView.onDestroy()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::map3DView.isInitialized) map3DView.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (::map3DView.isInitialized) map3DView.onLowMemory()
    }
}

private fun createMapConfig(): Map3DInitConfig =
    Map3DInitConfig.create(
        centerLat = 52.1,
        centerLng = 5.3,
        centerAlt = 0.0,
        heading = 0.0,
        tilt = 45.0,
        roll = 0.0,
        range = 2_000_000.0,
        minAltitude = 0.0,
        maxAltitude = 63_170_000.0,
        minHeading = 0.0,
        maxHeading = 360.0,
        minTilt = 0.0,
        maxTilt = 90.0,
        bounds = null,
        mapMode = Map3DMode.HYBRID,
        mapId = null,
        language = Locale.getDefault().language,
        region = Locale.getDefault().country,
    )

@Composable
private fun GodsEyeScreen(
    map3DView: Map3DView,
    map3D: GoogleMap3D?,
    mapReady: Boolean,
    mapError: String?,
) {
    var showLayers by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<SelectedContact?>(null) }
    var diagnostics by remember { mutableStateOf("renderer initialiseren…") }

    val enabled = remember {
        mutableStateMapOf(
            LiveLayerId.FLIGHTS to false,
            LiveLayerId.MILITARY to false,
            LiveLayerId.EARTHQUAKES to false,
            LiveLayerId.ISS to true,
            LiveLayerId.LAUNCHES to false,
        )
    }

    val uiStates = remember {
        mutableStateMapOf<LiveLayerId, LayerUiState>().apply {
            LiveLayerId.entries.forEach { put(it, LayerUiState()) }
        }
    }

    val controller = remember(map3D) {
        map3D?.let { map ->
            LiveLayerController(
                map = map,
                onLayerState = { layer, state -> uiStates[layer] = state },
                onSelection = { contact -> selected = contact },
            )
        }
    }

    DisposableEffect(controller) {
        onDispose { controller?.close() }
    }

    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        enabled.forEach { (layer, isEnabled) ->
            c.setEnabled(layer, isEnabled)
        }

        while (true) {
            diagnostics = c.diagnosticsText()
            kotlinx.coroutines.delay(1_000L)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Map3DHost(
            map3DView = map3DView,
            modifier = Modifier.fillMaxSize(),
        )

        Card(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = 14.dp, vertical = 44.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.76f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp)) {
                Text(
                    text = "GOD'S EYE",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = if (mapReady) "NATIVE INTELLIGENCE CONSOLE · ONLINE"
                    else "Native 3D engine starting…",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (mapReady) Color(0xFF7CFFB2) else Color.LightGray
                )
            }
        }

        if (showLayers) {
            LayerPanel(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(top = 92.dp, bottom = 88.dp, end = 10.dp),
                enabled = enabled,
                states = uiStates,
                diagnostics = diagnostics,
                onToggle = { layer, checked ->
                    enabled[layer] = checked
                    controller?.setEnabled(layer, checked)
                },
                onClose = { showLayers = false },
            )
        }

        selected?.let { contact ->
            SelectedContactCard(
                contact = contact,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, end = 12.dp, bottom = 82.dp),
                onClose = { selected = null }
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(14.dp)
                .background(
                    Color.Black.copy(alpha = 0.78f),
                    RoundedCornerShape(18.dp)
                )
                .padding(horizontal = 14.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(17.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomChip("🌍") {
                map3D?.stopCameraAnimation()
                map3D?.setCamera(
                    camera {
                        center = latLngAltitude {
                            latitude = 22.0
                            longitude = 8.0
                            altitude = 0.0
                        }
                        heading = 0.0
                        tilt = 0.0
                        roll = 0.0
                        range = 20_000_000.0
                    }
                )
            }
            BottomChip("LAYERS") { showLayers = !showLayers }
            BottomChip("UAP") { showLayers = true }
            BottomChip(
                if (enabled[LiveLayerId.ISS] == true) "SAT ●" else "SAT"
            ) {
                val next = enabled[LiveLayerId.ISS] != true
                enabled[LiveLayerId.ISS] = next
                controller?.setEnabled(LiveLayerId.ISS, next)
            }
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
private fun BottomChip(text: String, onClick: (() -> Unit)? = null) {
    Text(
        text = text,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        color = Color.White,
        style = MaterialTheme.typography.labelLarge
    )
}

@Composable
private fun LayerPanel(
    modifier: Modifier,
    enabled: Map<LiveLayerId, Boolean>,
    states: Map<LiveLayerId, LayerUiState>,
    diagnostics: String,
    onToggle: (LiveLayerId, Boolean) -> Unit,
    onClose: () -> Unit,
) {
    Card(
        modifier = modifier
            .width(310.dp)
            .fillMaxHeight(0.82f),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xEE101418)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("LIVE LAYERS", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Native bronnen uit God's Eye View",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Text(
                        diagnostics,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF7CE8FF)
                    )
                }
                Text(
                    "SLUIT",
                    modifier = Modifier.clickable(onClick = onClose),
                    color = Color(0xFF7CE8FF),
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Spacer(Modifier.padding(4.dp))
            HorizontalDivider(color = Color.DarkGray)

            LiveLayerId.entries.forEach { layer ->
                val state = states[layer] ?: LayerUiState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(layer.title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            when {
                                state.error != null -> state.error
                                state.loading -> "laden…"
                                state.count > 0 -> {
                                    val total = state.totalCount
                                    if (total > state.count) {
                                        "${state.count} van $total zichtbaar · ${layer.source}"
                                    } else {
                                        "${state.count} contacten · ${layer.source}"
                                    }
                                }
                                else -> layer.source
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                state.error != null -> Color(0xFFFF7D7D)
                                state.count > 0 -> Color(0xFF7CFFB2)
                                else -> Color.Gray
                            }
                        )
                    }
                    Switch(
                        checked = enabled[layer] == true,
                        onCheckedChange = { onToggle(layer, it) }
                    )
                }
                HorizontalDivider(color = Color(0xFF252B30))
            }

            Text(
                "VOLGENDE PORTS",
                modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF7CE8FF)
            )

            val nextLayers = listOf(
                "Traffic",
                "CCTV / publieke camera's",
                "Radio",
                "Bikeshare",
                "AIS schepen",
                "Military installations",
                "Military awareness",
                "Datacenters",
                "Dammen",
                "Onderzeese kabels",
                "NASA FIRMS branden",
                "UAP Intelligence",
            )
            nextLayers.forEach { name ->
                Text(
                    "○ $name",
                    modifier = Modifier.padding(vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
            }
        }
    }
}

@Composable
private fun SelectedContactCard(
    contact: SelectedContact,
    modifier: Modifier,
    onClose: () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.83f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(contact.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    contact.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF7CE8FF)
                )
                if (contact.details.isNotBlank()) {
                    Text(
                        contact.details,
                        modifier = Modifier.padding(top = 5.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                }
            }
            Text(
                "×",
                modifier = Modifier.clickable(onClick = onClose),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
        }
    }
}

@Composable
private fun Map3DHost(
    map3DView: Map3DView,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { map3DView },
        update = { _ -> Unit },
    )
}
