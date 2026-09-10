package com.mindlizzard.godseye

import android.graphics.Color
import com.google.android.gms.maps3d.GoogleMap3D
import com.google.android.gms.maps3d.model.AltitudeMode
import com.google.android.gms.maps3d.model.CollisionBehavior
import com.google.android.gms.maps3d.model.Glyph
import com.google.android.gms.maps3d.model.Marker
import com.google.android.gms.maps3d.model.latLngAltitude
import com.google.android.gms.maps3d.model.markerOptions
import com.google.android.gms.maps3d.model.pinConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

enum class LiveLayerId(val title: String, val source: String) {
    FLIGHTS("Vluchten", "OpenSky"),
    MILITARY("Militair", "adsb.lol"),
    EARTHQUAKES("Aardbevingen", "USGS"),
    ISS("ISS", "Where The ISS At"),
    LAUNCHES("Rocket launches", "The Space Devs"),
}

data class LayerUiState(
    val loading: Boolean = false,
    val count: Int = 0,
    val error: String? = null,
)

data class SelectedContact(
    val title: String,
    val subtitle: String,
    val details: String,
)

private data class LayerPoint(
    val id: String,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double,
    val altitudeMode: AltitudeMode,
    val details: String,
)

class LiveLayerController(
    private val map: GoogleMap3D,
    private val onLayerState: (LiveLayerId, LayerUiState) -> Unit,
    private val onSelection: (SelectedContact) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val jobs = mutableMapOf<LiveLayerId, Job>()
    private val markers = mutableMapOf<LiveLayerId, MutableList<Marker>>()

    fun setEnabled(layer: LiveLayerId, enabled: Boolean) {
        if (enabled) start(layer) else stop(layer)
    }

    fun close() {
        LiveLayerId.entries.forEach { stop(it) }
        scope.coroutineContext[Job]?.cancel()
    }

    private fun start(layer: LiveLayerId) {
        if (jobs[layer]?.isActive == true) return

        jobs[layer] = scope.launch {
            while (isActive) {
                onLayerState(layer, LayerUiState(loading = true, count = markers[layer]?.size ?: 0))
                try {
                    val points = withContext(Dispatchers.IO) {
                        when (layer) {
                            LiveLayerId.FLIGHTS -> LiveSources.fetchFlights()
                            LiveLayerId.MILITARY -> LiveSources.fetchMilitary()
                            LiveLayerId.EARTHQUAKES -> LiveSources.fetchEarthquakes()
                            LiveLayerId.ISS -> LiveSources.fetchIss()
                            LiveLayerId.LAUNCHES -> LiveSources.fetchLaunches()
                        }
                    }
                    replaceMarkers(layer, points)
                    onLayerState(layer, LayerUiState(count = points.size))
                } catch (t: Throwable) {
                    val short = t.message?.take(90) ?: t.javaClass.simpleName
                    onLayerState(layer, LayerUiState(
                        count = markers[layer]?.size ?: 0,
                        error = short
                    ))
                }

                delay(refreshInterval(layer))
            }
        }
    }

    private fun stop(layer: LiveLayerId) {
        jobs.remove(layer)?.cancel()
        markers.remove(layer)?.forEach { marker ->
            runCatching { marker.remove() }
        }
        onLayerState(layer, LayerUiState())
    }

    private fun replaceMarkers(layer: LiveLayerId, points: List<LayerPoint>) {
        markers.remove(layer)?.forEach { runCatching { it.remove() } }
        val newMarkers = mutableListOf<Marker>()
        val style = styleFor(layer)

        points.forEach { point ->
            val marker = map.addMarker(
                markerOptions {
                    id = "${layer.name}:${point.id}"
                    position = latLngAltitude {
                        latitude = point.latitude
                        longitude = point.longitude
                        altitude = point.altitudeMeters
                    }
                    label = point.label.take(42)
                    altitudeMode = point.altitudeMode
                    isExtruded = layer == LiveLayerId.FLIGHTS ||
                        layer == LiveLayerId.MILITARY ||
                        layer == LiveLayerId.ISS
                    isDrawnWhenOccluded = true
                    collisionBehavior = CollisionBehavior.OPTIONAL_AND_HIDES_LOWER_PRIORITY
                    setStyle(
                        pinConfiguration {
                            backgroundColor = style.first
                            borderColor = style.second
                            scale = style.third
                            setGlyph(Glyph.fromColor(Color.WHITE))
                        }
                    )
                }
            )

            marker?.let { m ->
                m.setClickListener {
                    scope.launch(Dispatchers.Main) {
                        onSelection(
                            SelectedContact(
                                title = point.label,
                                subtitle = layer.title.uppercase(),
                                details = point.details
                            )
                        )
                    }
                }
                newMarkers += m
            }
        }

        markers[layer] = newMarkers
    }

    private fun refreshInterval(layer: LiveLayerId): Long = when (layer) {
        LiveLayerId.ISS -> 10_000L
        LiveLayerId.MILITARY -> 30_000L
        LiveLayerId.FLIGHTS -> 60_000L
        LiveLayerId.EARTHQUAKES -> 120_000L
        LiveLayerId.LAUNCHES -> 15 * 60_000L
    }

    private fun styleFor(layer: LiveLayerId): Triple<Int, Int, Double> = when (layer) {
        LiveLayerId.FLIGHTS -> Triple(Color.rgb(40, 170, 255), Color.WHITE, 0.62)
        LiveLayerId.MILITARY -> Triple(Color.rgb(255, 184, 0), Color.WHITE, 0.66)
        LiveLayerId.EARTHQUAKES -> Triple(Color.rgb(255, 82, 82), Color.WHITE, 0.58)
        LiveLayerId.ISS -> Triple(Color.rgb(163, 113, 255), Color.WHITE, 0.82)
        LiveLayerId.LAUNCHES -> Triple(Color.rgb(255, 120, 45), Color.WHITE, 0.68)
    }
}

private object LiveSources {
    private val clockFormatter = DateTimeFormatter
        .ofPattern("dd MMM HH:mm")
        .withZone(ZoneId.systemDefault())

    fun fetchEarthquakes(): List<LayerPoint> {
        val json = JSONObject(
            get("https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/all_day.geojson")
        )
        val features = json.getJSONArray("features")
        val out = mutableListOf<LayerPoint>()

        for (i in 0 until features.length()) {
            val feature = features.getJSONObject(i)
            val geometry = feature.getJSONObject("geometry")
            val coordinates = geometry.getJSONArray("coordinates")
            val props = feature.getJSONObject("properties")

            val lon = coordinates.optDouble(0, Double.NaN)
            val lat = coordinates.optDouble(1, Double.NaN)
            val depthKm = coordinates.optDouble(2, 0.0)
            if (!lat.isFinite() || !lon.isFinite()) continue

            val mag = props.optDouble("mag", 0.0)
            val place = props.optString("place", "Onbekende locatie")
            val time = props.optLong("time", 0L)
            val id = feature.optString("id", "eq-$i")

            out += LayerPoint(
                id = id,
                label = "M${"%.1f".format(mag)} · $place",
                latitude = lat,
                longitude = lon,
                altitudeMeters = 0.0,
                altitudeMode = AltitudeMode.CLAMP_TO_GROUND,
                details = "Diepte ${depthKm.roundToInt()} km · ${formatEpochMillis(time)} · USGS"
            )
        }

        return out
            .sortedByDescending {
                Regex("""M([0-9.]+)""").find(it.label)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 0.0
            }
            .take(220)
    }

    fun fetchMilitary(): List<LayerPoint> {
        val json = JSONObject(get("https://api.adsb.lol/v2/mil"))
        val aircraft = json.optJSONArray("ac") ?: JSONArray()
        val out = mutableListOf<LayerPoint>()

        for (i in 0 until aircraft.length()) {
            val ac = aircraft.optJSONObject(i) ?: continue
            val lat = ac.optDouble("lat", Double.NaN)
            val lon = ac.optDouble("lon", Double.NaN)
            if (!lat.isFinite() || !lon.isFinite()) continue

            val hex = ac.optString("hex", "mil-$i")
            val flight = ac.optString("flight", "").trim().ifBlank { hex.uppercase() }
            val altRaw = ac.opt("alt_baro")
            val altitudeFeet = when (altRaw) {
                is Number -> altRaw.toDouble()
                is String -> altRaw.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }
            val gs = ac.optDouble("gs", Double.NaN)
            val track = ac.optDouble("track", Double.NaN)
            val type = ac.optString("t", "").trim()

            val extra = buildList {
                if (type.isNotBlank()) add(type)
                if (gs.isFinite()) add("${gs.roundToInt()} kt")
                if (track.isFinite()) add("${track.roundToInt()}°")
                add("ICAO ${hex.uppercase()}")
                add("adsb.lol")
            }.joinToString(" · ")

            out += LayerPoint(
                id = hex,
                label = flight,
                latitude = lat,
                longitude = lon,
                altitudeMeters = altitudeFeet * 0.3048,
                altitudeMode = AltitudeMode.ABSOLUTE,
                details = extra
            )
        }
        return out.take(500)
    }

    fun fetchFlights(): List<LayerPoint> {
        // Bounding box keeps the anonymous OpenSky request small enough for a phone.
        // Western Europe is used for the first native port; camera-aware bboxes come next.
        val url = "https://opensky-network.org/api/states/all" +
            "?lamin=35&lomin=-15&lamax=60&lomax=30"
        val json = JSONObject(get(url))
        val states = json.optJSONArray("states") ?: JSONArray()
        val out = mutableListOf<LayerPoint>()

        for (i in 0 until states.length()) {
            val state = states.optJSONArray(i) ?: continue
            if (state.length() < 14) continue

            val lon = state.optNullableDouble(5)
            val lat = state.optNullableDouble(6)
            if (lat == null || lon == null) continue

            val icao = state.optString(0, "").trim().ifBlank { "flt-$i" }
            val callsign = state.optString(1, "").trim().ifBlank { icao.uppercase() }
            val country = state.optString(2, "").trim()
            val baro = state.optNullableDouble(7)
            val geo = state.optNullableDouble(13)
            val velocity = state.optNullableDouble(9)
            val track = state.optNullableDouble(10)
            val onGround = state.optBoolean(8, false)

            val details = buildList {
                if (country.isNotBlank()) add(country)
                if (velocity != null) add("${(velocity * 1.94384).roundToInt()} kt")
                if (track != null) add("${track.roundToInt()}°")
                add(if (onGround) "ground" else "airborne")
                add("ICAO ${icao.uppercase()}")
                add("OpenSky")
            }.joinToString(" · ")

            out += LayerPoint(
                id = icao,
                label = callsign,
                latitude = lat,
                longitude = lon,
                altitudeMeters = if (onGround) 0.0 else (geo ?: baro ?: 0.0),
                altitudeMode = if (onGround) AltitudeMode.CLAMP_TO_GROUND else AltitudeMode.ABSOLUTE,
                details = details
            )
        }
        return out.take(800)
    }

    fun fetchIss(): List<LayerPoint> {
        val json = JSONObject(get("https://api.wheretheiss.at/v1/satellites/25544"))
        val lat = json.getDouble("latitude")
        val lon = json.getDouble("longitude")
        val altitudeKm = json.optDouble("altitude", 420.0)
        val velocityKmh = json.optDouble("velocity", Double.NaN)
        val visibility = json.optString("visibility", "")

        val details = buildList {
            add("NORAD 25544")
            add("${altitudeKm.roundToInt()} km")
            if (velocityKmh.isFinite()) add("${velocityKmh.roundToInt()} km/h")
            if (visibility.isNotBlank()) add(visibility)
        }.joinToString(" · ")

        return listOf(
            LayerPoint(
                id = "25544",
                label = "ISS",
                latitude = lat,
                longitude = lon,
                altitudeMeters = altitudeKm * 1000.0,
                altitudeMode = AltitudeMode.ABSOLUTE,
                details = details
            )
        )
    }

    fun fetchLaunches(): List<LayerPoint> {
        val url = "https://ll.thespacedevs.com/2.3.0/launches/upcoming/" +
            "?limit=12&ordering=net&mode=normal&format=json"
        val json = JSONObject(get(url))
        val results = json.optJSONArray("results") ?: JSONArray()
        val out = mutableListOf<LayerPoint>()

        for (i in 0 until results.length()) {
            val launch = results.optJSONObject(i) ?: continue
            val pad = launch.optJSONObject("pad") ?: continue
            val lat = pad.optString("latitude", "").toDoubleOrNull()
                ?: pad.optDouble("latitude", Double.NaN).takeIf { it.isFinite() }
                ?: continue
            val lon = pad.optString("longitude", "").toDoubleOrNull()
                ?: pad.optDouble("longitude", Double.NaN).takeIf { it.isFinite() }
                ?: continue

            val id = launch.optString("id", "launch-$i")
            val name = launch.optString("name", "Rocket launch")
            val net = launch.optString("net", "")
            val status = launch.optJSONObject("status")?.optString("name", "") ?: ""
            val location = pad.optJSONObject("location")?.optString("name", "")
                ?: pad.optString("name", "")

            val timeText = runCatching {
                clockFormatter.format(Instant.parse(net))
            }.getOrDefault(net)

            out += LayerPoint(
                id = id,
                label = name,
                latitude = lat,
                longitude = lon,
                altitudeMeters = 0.0,
                altitudeMode = AltitudeMode.CLAMP_TO_GROUND,
                details = listOf(status, timeText, location, "The Space Devs")
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
            )
        }

        return out
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 12_000
            connection.readTimeout = 15_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "GodsEye-Android/0.3 personal-native-client")

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code ${body.take(80)}".trim())
            }
            return body
        } finally {
            connection.disconnect()
        }
    }

    private fun formatEpochMillis(epochMillis: Long): String =
        if (epochMillis <= 0L) "" else clockFormatter.format(Instant.ofEpochMilli(epochMillis))

    private fun JSONArray.optNullableDouble(index: Int): Double? {
        if (index < 0 || index >= length() || isNull(index)) return null
        val value = opt(index)
        return when (value) {
            is Number -> value.toDouble().takeIf { it.isFinite() }
            is String -> value.toDoubleOrNull()?.takeIf { it.isFinite() }
            else -> null
        }
    }
}
