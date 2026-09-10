package com.mindlizzard.godseye

import android.graphics.Color
import com.google.android.gms.maps3d.GoogleMap3D
import com.google.android.gms.maps3d.model.AltitudeMode
import com.google.android.gms.maps3d.model.CollisionBehavior
import com.google.android.gms.maps3d.model.ImageView
import com.google.android.gms.maps3d.model.Marker
import com.google.android.gms.maps3d.model.latLngAltitude
import com.google.android.gms.maps3d.model.markerOptions
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
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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
    val totalCount: Int = 0,
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
    val altitudeMode: Int,
    val details: String,
    val speedMps: Double? = null,
    val trackDegrees: Double? = null,
    val moving: Boolean = false,
)

private data class LiveMarkerState(
    var marker: Marker,
    var point: LayerPoint,
    var renderLat: Double,
    var renderLon: Double,
    var renderAltitudeMeters: Double,
    var headingBucket: Int? = null,
)

class LiveLayerController(
    private val map: GoogleMap3D,
    private val onLayerState: (LiveLayerId, LayerUiState) -> Unit,
    private val onSelection: (SelectedContact) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val jobs = mutableMapOf<LiveLayerId, Job>()
    private val markers = mutableMapOf<LiveLayerId, MutableMap<String, LiveMarkerState>>()
    private val latestPoints = mutableMapOf<LiveLayerId, List<LayerPoint>>()
    private val enabledLayers = mutableSetOf<LiveLayerId>()

    private var cameraCenterLat = runCatching { map.camera.center.latitude }.getOrDefault(52.1)
    private var cameraCenterLon = runCatching { map.camera.center.longitude }.getOrDefault(5.3)
    private var cameraRangeMeters = runCatching { map.camera.range }.getOrDefault(2_000_000.0)
    private var cameraResyncJob: Job? = null
    @Volatile private var closed = false

    init {
        // Maps 3D individual markers are much heavier than Cesium's batched BillboardCollection.
        // Rebuild the visible LOD only after camera movement settles, never on every frame.
        map.addOnCameraMoveListener {
            if (closed) return@addOnCameraMoveListener

            val camera = runCatching { map.camera }.getOrNull() ?: return@addOnCameraMoveListener
            cameraCenterLat = camera.center.latitude
            cameraCenterLon = camera.center.longitude
            cameraRangeMeters = camera.range

            cameraResyncJob?.cancel()
            cameraResyncJob = scope.launch {
                delay(CAMERA_LOD_DEBOUNCE_MS)
                enabledLayers.toList().forEach { renderLayerFromCache(it) }
            }
        }
    }

    fun setEnabled(layer: LiveLayerId, enabled: Boolean) {
        if (enabled) {
            enabledLayers += layer
            start(layer)
        } else {
            enabledLayers -= layer
            stop(layer)
        }
    }

    fun close() {
        closed = true
        cameraResyncJob?.cancel()
        LiveLayerId.entries.forEach { stop(it) }
        scope.coroutineContext[Job]?.cancel()
    }

    private fun start(layer: LiveLayerId) {
        if (jobs[layer]?.isActive == true) return

        jobs[layer] = scope.launch {
            while (isActive) {
                onLayerState(
                    layer,
                    LayerUiState(
                        loading = true,
                        count = markers[layer]?.size ?: 0,
                        totalCount = latestPoints[layer]?.size ?: 0
                    )
                )

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

                    latestPoints[layer] = points
                    renderLayerFromCache(layer)
                } catch (t: Throwable) {
                    val short = t.message?.take(90) ?: t.javaClass.simpleName
                    onLayerState(
                        layer,
                        LayerUiState(
                            count = markers[layer]?.size ?: 0,
                            totalCount = latestPoints[layer]?.size ?: 0,
                            error = short
                        )
                    )
                }

                var waited = 0L
                val interval = refreshInterval(layer)
                while (isActive && waited < interval) {
                    delay(MOTION_TICK_MS)
                    advanceMovingContacts(layer)
                    waited += MOTION_TICK_MS
                }
            }
        }
    }

    private fun stop(layer: LiveLayerId) {
        jobs.remove(layer)?.cancel()
        markers.remove(layer)?.values?.forEach { state ->
            runCatching { state.marker.remove() }
        }
        onLayerState(layer, LayerUiState(totalCount = latestPoints[layer]?.size ?: 0))
    }

    private fun renderLayerFromCache(layer: LiveLayerId) {
        if (layer !in enabledLayers) return
        val allPoints = latestPoints[layer] ?: return
        val visible = selectForCamera(layer, allPoints)
        syncMarkers(layer, visible)
        onLayerState(
            layer,
            LayerUiState(
                count = visible.size,
                totalCount = allPoints.size
            )
        )
    }

    private fun selectForCamera(layer: LiveLayerId, points: List<LayerPoint>): List<LayerPoint> {
        if (layer == LiveLayerId.ISS || layer == LiveLayerId.LAUNCHES) return points

        val cap = markerCap(layer, cameraRangeMeters)
        if (points.size <= cap) return points

        // Keep the nearest contacts to the current camera center. This is the native
        // Maps 3D LOD equivalent of the upstream Cesium billboard batching strategy.
        return points
            .asSequence()
            .sortedBy { approximateAngularDistanceSq(it.latitude, it.longitude) }
            .take(cap)
            .toList()
    }

    private fun markerCap(layer: LiveLayerId, rangeMeters: Double): Int {
        val far = rangeMeters > 3_500_000.0
        val medium = rangeMeters > 1_500_000.0

        return when (layer) {
            LiveLayerId.FLIGHTS -> when {
                far -> 55
                medium -> 85
                else -> 125
            }
            LiveLayerId.MILITARY -> when {
                far -> 20
                medium -> 30
                else -> 45
            }
            LiveLayerId.EARTHQUAKES -> when {
                far -> 35
                medium -> 55
                else -> 80
            }
            LiveLayerId.ISS -> 1
            LiveLayerId.LAUNCHES -> 16
        }
    }

    private fun approximateAngularDistanceSq(lat: Double, lon: Double): Double {
        val dLat = Math.toRadians(lat - cameraCenterLat)
        val dLon = Math.toRadians(lon - cameraCenterLon) *
            cos(Math.toRadians(cameraCenterLat))
        return dLat * dLat + dLon * dLon
    }

    private fun syncMarkers(layer: LiveLayerId, points: List<LayerPoint>) {
        val layerMarkers = markers.getOrPut(layer) { linkedMapOf() }
        val incomingIds = points.asSequence().map { it.id }.toHashSet()

        val staleIds = layerMarkers.keys.filter { it !in incomingIds }
        staleIds.forEach { id ->
            layerMarkers.remove(id)?.let { runCatching { it.marker.remove() } }
        }

        points.forEach { incoming ->
            val existing = layerMarkers[incoming.id]

            val correctedPoint =
                if (existing != null && layer == LiveLayerId.ISS && incoming.moving) {
                    val inferredTrack = bearingDegrees(
                        existing.point.latitude,
                        existing.point.longitude,
                        incoming.latitude,
                        incoming.longitude
                    )
                    incoming.copy(trackDegrees = inferredTrack)
                } else {
                    incoming
                }

            val desiredBucket = headingBucketFor(layer, correctedPoint.trackDegrees)

            if (existing != null) {
                // Camera-only LOD resync? Keep the dead-reckoned render position.
                if (existing.point == correctedPoint && desiredBucket == existing.headingBucket) {
                    return@forEach
                }

                existing.point = correctedPoint
                existing.headingBucket = desiredBucket
                existing.renderLat = correctedPoint.latitude
                existing.renderLon = correctedPoint.longitude
                existing.renderAltitudeMeters = correctedPoint.altitudeMeters

                upsertMarker(layer, existing)
            } else {
                val marker = createMarker(layer, correctedPoint) ?: return@forEach
                val state = LiveMarkerState(
                    marker = marker,
                    point = correctedPoint,
                    renderLat = correctedPoint.latitude,
                    renderLon = correctedPoint.longitude,
                    renderAltitudeMeters = correctedPoint.altitudeMeters,
                    headingBucket = desiredBucket
                )
                layerMarkers[correctedPoint.id] = state
                bindClickListener(layer, correctedPoint.id, marker, correctedPoint)
            }
        }
    }

    private fun bindClickListener(
        layer: LiveLayerId,
        id: String,
        marker: Marker,
        fallback: LayerPoint,
    ) {
        marker.setClickListener {
            scope.launch(Dispatchers.Main) {
                val latest = markers[layer]?.get(id)?.point ?: fallback
                onSelection(
                    SelectedContact(
                        title = latest.label,
                        subtitle = layer.title.uppercase(),
                        details = latest.details
                    )
                )
            }
        }
    }

    private fun createMarker(layer: LiveLayerId, point: LayerPoint): Marker? =
        addOrUpdateMarker(
            layer = layer,
            point = point,
            latitude = point.latitude,
            longitude = point.longitude,
            altitudeMeters = point.altitudeMeters,
            overrideId = null
        )

    private fun addOrUpdateMarker(
        layer: LiveLayerId,
        point: LayerPoint,
        latitude: Double,
        longitude: Double,
        altitudeMeters: Double,
        overrideId: String?,
    ): Marker? =
        map.addMarker(
            markerOptions {
                id = overrideId ?: "${layer.name}:${point.id}"
                position = latLngAltitude {
                    this.latitude = latitude
                    this.longitude = longitude
                    altitude = altitudeMeters
                }

                if (layer == LiveLayerId.ISS) {
                    label = point.label
                }

                altitudeMode = point.altitudeMode
                isExtruded = false

                // Never force the far side of Earth to render hundreds of contacts.
                // The upstream web app also has a horizon occluder for this reason.
                isDrawnWhenOccluded = false
                collisionBehavior = CollisionBehavior.OPTIONAL_AND_HIDES_LOWER_PRIORITY
                setStyle(ImageView(iconFor(layer, point.trackDegrees)))
            }
        )

    private fun upsertMarker(layer: LiveLayerId, state: LiveMarkerState) {
        // Maps 3D objects are largely immutable. Google's own Compose helper updates
        // markers by calling addMarker again with the SAME underlying ID.
        val updated = addOrUpdateMarker(
            layer = layer,
            point = state.point,
            latitude = state.renderLat,
            longitude = state.renderLon,
            altitudeMeters = state.renderAltitudeMeters,
            overrideId = state.marker.id
        ) ?: return

        state.marker = updated
        bindClickListener(layer, state.point.id, updated, state.point)
    }

    private fun iconFor(layer: LiveLayerId, trackDegrees: Double?): Int = when (layer) {
        LiveLayerId.FLIGHTS -> aircraftIcon(headingBucket(trackDegrees) ?: 0, military = false)
        LiveLayerId.MILITARY -> aircraftIcon(headingBucket(trackDegrees) ?: 0, military = true)
        LiveLayerId.EARTHQUAKES -> R.drawable.ic_contact_quake
        LiveLayerId.ISS -> R.drawable.ic_contact_satellite
        LiveLayerId.LAUNCHES -> R.drawable.ic_contact_rocket
    }

    private fun headingBucketFor(layer: LiveLayerId, trackDegrees: Double?): Int? =
        if (layer == LiveLayerId.FLIGHTS || layer == LiveLayerId.MILITARY) {
            headingBucket(trackDegrees)
        } else {
            null
        }

    private fun headingBucket(trackDegrees: Double?): Int? {
        val raw = trackDegrees?.takeIf { it.isFinite() } ?: return null
        val normalized = ((raw % 360.0) + 360.0) % 360.0
        return (((normalized + 11.25) / 22.5).toInt()) % 16
    }

    private fun aircraftIcon(bucket: Int, military: Boolean): Int =
        if (military) {
            when (bucket) {
                0 -> R.drawable.ic_contact_military_000
                1 -> R.drawable.ic_contact_military_023
                2 -> R.drawable.ic_contact_military_045
                3 -> R.drawable.ic_contact_military_068
                4 -> R.drawable.ic_contact_military_090
                5 -> R.drawable.ic_contact_military_113
                6 -> R.drawable.ic_contact_military_135
                7 -> R.drawable.ic_contact_military_158
                8 -> R.drawable.ic_contact_military_180
                9 -> R.drawable.ic_contact_military_203
                10 -> R.drawable.ic_contact_military_225
                11 -> R.drawable.ic_contact_military_248
                12 -> R.drawable.ic_contact_military_270
                13 -> R.drawable.ic_contact_military_293
                14 -> R.drawable.ic_contact_military_315
                else -> R.drawable.ic_contact_military_338
            }
        } else {
            when (bucket) {
                0 -> R.drawable.ic_contact_aircraft_000
                1 -> R.drawable.ic_contact_aircraft_023
                2 -> R.drawable.ic_contact_aircraft_045
                3 -> R.drawable.ic_contact_aircraft_068
                4 -> R.drawable.ic_contact_aircraft_090
                5 -> R.drawable.ic_contact_aircraft_113
                6 -> R.drawable.ic_contact_aircraft_135
                7 -> R.drawable.ic_contact_aircraft_158
                8 -> R.drawable.ic_contact_aircraft_180
                9 -> R.drawable.ic_contact_aircraft_203
                10 -> R.drawable.ic_contact_aircraft_225
                11 -> R.drawable.ic_contact_aircraft_248
                12 -> R.drawable.ic_contact_aircraft_270
                13 -> R.drawable.ic_contact_aircraft_293
                14 -> R.drawable.ic_contact_aircraft_315
                else -> R.drawable.ic_contact_aircraft_338
            }
        }

    private fun advanceMovingContacts(layer: LiveLayerId) {
        if (layer != LiveLayerId.FLIGHTS &&
            layer != LiveLayerId.MILITARY &&
            layer != LiveLayerId.ISS
        ) return

        // The SDK's native Marker API is not a batched sprite collection.
        // One real position update per second per visible contact keeps touch/zoom fluid.
        markers[layer]?.values?.forEach { state ->
            val point = state.point
            if (!point.moving) return@forEach

            val speed = point.speedMps ?: return@forEach
            val track = point.trackDegrees ?: return@forEach
            if (speed <= 0.5 || !speed.isFinite() || !track.isFinite()) return@forEach

            val maxSpeedMps = if (layer == LiveLayerId.ISS) 9_000.0 else 1_200.0
            val distance = speed.coerceAtMost(maxSpeedMps) * (MOTION_TICK_MS / 1000.0)
            val moved = destinationPoint(
                state.renderLat,
                state.renderLon,
                track,
                distance
            )

            state.renderLat = moved.first
            state.renderLon = moved.second

            // Important: setPosition() compiles in 0.2.2 but does not reliably invalidate
            // the rendered marker. Same-ID addMarker is the SDK-supported upsert path.
            upsertMarker(layer, state)
        }
    }

    private fun refreshInterval(layer: LiveLayerId): Long = when (layer) {
        LiveLayerId.ISS -> 5_000L
        LiveLayerId.MILITARY -> 20_000L
        LiveLayerId.FLIGHTS -> 60_000L
        LiveLayerId.EARTHQUAKES -> 120_000L
        LiveLayerId.LAUNCHES -> 15 * 60_000L
    }

    private fun destinationPoint(
        latDegrees: Double,
        lonDegrees: Double,
        bearingDegrees: Double,
        distanceMeters: Double,
    ): Pair<Double, Double> {
        val angularDistance = distanceMeters / EARTH_RADIUS_M
        val bearing = Math.toRadians(bearingDegrees)
        val lat1 = Math.toRadians(latDegrees)
        val lon1 = Math.toRadians(lonDegrees)

        val lat2 = asin(
            sin(lat1) * cos(angularDistance) +
                cos(lat1) * sin(angularDistance) * cos(bearing)
        )
        val lon2 = lon1 + atan2(
            sin(bearing) * sin(angularDistance) * cos(lat1),
            cos(angularDistance) - sin(lat1) * sin(lat2)
        )

        var normalizedLon = Math.toDegrees(lon2)
        normalizedLon = ((normalizedLon + 540.0) % 360.0) - 180.0

        return Math.toDegrees(lat2) to normalizedLon
    }

    private fun bearingDegrees(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double,
    ): Double {
        val lat1 = Math.toRadians(fromLat)
        val lat2 = Math.toRadians(toLat)
        val deltaLon = Math.toRadians(toLon - fromLon)
        val y = sin(deltaLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) -
            sin(lat1) * cos(lat2) * cos(deltaLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    companion object {
        private const val MOTION_TICK_MS = 1_000L
        private const val CAMERA_LOD_DEBOUNCE_MS = 450L
        private const val EARTH_RADIUS_M = 6_371_000.0
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
                Regex("""M([0-9.]+)""")
                    .find(it.label)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toDoubleOrNull() ?: 0.0
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

            val gsKnots = ac.optDouble("gs", Double.NaN)
            val track = ac.optDouble("track", Double.NaN)
            val type = ac.optString("t", "").trim()
            val onGround = altRaw is String && altRaw.equals("ground", ignoreCase = true)

            val extra = buildList {
                if (type.isNotBlank()) add(type)
                if (gsKnots.isFinite()) add("${gsKnots.roundToInt()} kt")
                if (track.isFinite()) add("${track.roundToInt()}°")
                add(if (onGround) "ground" else "airborne")
                add("ICAO ${hex.uppercase()}")
                add("adsb.lol")
            }.joinToString(" · ")

            out += LayerPoint(
                id = hex,
                label = flight,
                latitude = lat,
                longitude = lon,
                altitudeMeters = if (onGround) 0.0 else altitudeFeet * 0.3048,
                altitudeMode = if (onGround) AltitudeMode.CLAMP_TO_GROUND else AltitudeMode.ABSOLUTE,
                details = extra,
                speedMps = if (gsKnots.isFinite()) gsKnots * 0.514444 else null,
                trackDegrees = if (track.isFinite()) track else null,
                moving = !onGround && gsKnots.isFinite() && track.isFinite()
            )
        }
        return out.take(500)
    }

    fun fetchFlights(): List<LayerPoint> {
        // First Android port: keep the anonymous request bounded to Western Europe.
        // Later this becomes camera-aware, so we only fetch the part of Earth being viewed.
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
            val velocityMps = state.optNullableDouble(9)
            val track = state.optNullableDouble(10)
            val onGround = state.optBoolean(8, false)

            val details = buildList {
                if (country.isNotBlank()) add(country)
                if (velocityMps != null) add("${(velocityMps * 1.94384).roundToInt()} kt")
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
                details = details,
                speedMps = velocityMps,
                trackDegrees = track,
                moving = !onGround && velocityMps != null && track != null
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
            add("Where The ISS At")
        }.joinToString(" · ")

        return listOf(
            LayerPoint(
                id = "25544",
                label = "ISS",
                latitude = lat,
                longitude = lon,
                altitudeMeters = altitudeKm * 1000.0,
                altitudeMode = AltitudeMode.ABSOLUTE,
                details = details,
                speedMps = if (velocityKmh.isFinite()) velocityKmh / 3.6 else null,
                trackDegrees = null,
                moving = velocityKmh.isFinite()
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
            connection.setRequestProperty("User-Agent", "GodsEye-Android/0.4 personal-native-client")

            val code = connection.responseCode
            val stream =
                if (code in 200..299) connection.inputStream else connection.errorStream
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
        if (epochMillis <= 0L) ""
        else clockFormatter.format(Instant.ofEpochMilli(epochMillis))

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
