package com.example.campsitereservationapp.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.campsitereservationapp.ui.components.formatPrice
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.io.File

/*
 * The map itself, drawn with osmdroid and OpenStreetMap tiles, so it needs no API key.
 *
 * Tiles are recoloured dark forest green to match the app, campsites are price-tag pins (lime when
 * selected), and the search area is a dashed circle around the entrant or searched place.
 * Kept apart from CampsiteMapScreen so the screen's intent tests do not need map tiles.
 *
 * Outstanding issues: tiles need an internet connection; previously viewed tiles are cached.
 * OpenStreetMap's free tile servers are meant for light use, which suits a class demo; a
 * released app would need its own tile provider.
 */

/**
 * Shows campsite pins and the search circle on an OpenStreetMap map.
 *
 * @param campsites Campsites to pin.
 * @param selectedId Campsite whose pin is highlighted, or null.
 * @param center Place campsites are measured from, or null when every campsite is shown.
 * @param radiusKm Radius of the search circle in kilometres.
 * @param onPinClick Called with a campsite's ID when its pin is tapped.
 * @param onMapClick Called when empty map is tapped.
 */
@Composable
fun OsmCampsiteMap(
    campsites: List<MapCampsite>,
    selectedId: String?,
    center: MapCenter?,
    radiusKm: Int,
    onPinClick: (String) -> Unit,
    onMapClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val holder = remember { MapHolder(createMapView(context)) }
    holder.onPinClick = onPinClick
    holder.onMapClick = onMapClick

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> holder.map.onResume()
                Lifecycle.Event.ON_PAUSE -> holder.map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) holder.map.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            holder.map.onPause()
            holder.map.onDetach()
        }
    }

    Box(modifier) {
        AndroidView(
            factory = { holder.map },
            modifier = Modifier.fillMaxSize(),
            update = { holder.show(campsites, selectedId, center, radiusKm) }
        )
        // The tile licence requires this credit to be visible on the map.
        Text(
            MAP_ATTRIBUTION,
            Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 8.dp, bottom = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = PinedrawColors.TextMuted
        )
    }
}

/** Credit the map tiles' licence requires. */
const val MAP_ATTRIBUTION = "© OpenStreetMap contributors"

// OpenStreetMap's standard tiles.
private val osmTiles = XYTileSource(
    "OpenStreetMapStandard", 3, 19, 256, ".png", arrayOf("https://tile.openstreetmap.org/"), MAP_ATTRIBUTION
)

// OpenStreetMap's tile policy blocks requests that do not clearly name the app sending them.
private const val MAP_USER_AGENT = "PinedrawCampsiteMap/1.0 (Android; CMPUT 301 student project)"

// Keeps the MapView and what it last drew, so recomposing only redraws what changed.
private class MapHolder(val map: MapView) {
    var onPinClick: (String) -> Unit = {}
    var onMapClick: () -> Unit = {}
    private var drawnKey: Any? = null
    private var focusKey: Any? = null
    private var lastSelected: String? = null
    private val pinCache = mutableMapOf<Pair<String, Boolean>, BitmapDrawable>()

    fun show(campsites: List<MapCampsite>, selectedId: String?, center: MapCenter?, radiusKm: Int) {
        val key = listOf(campsites.map { it.campsite.id to it.campsite.pricePerNight }, selectedId, center, radiusKm)
        if (key != drawnKey) {
            drawnKey = key
            redraw(campsites, selectedId, center, radiusKm)
        }
        // Move the camera only when the search changes, not every time a pin is tapped.
        val newFocus = listOf(center, radiusKm, if (center == null) campsites.map { it.campsite.id } else null)
        if (newFocus != focusKey) {
            focusKey = newFocus
            val bounds = if (center != null) areaBounds(center.point, radiusKm) else pointsBounds(campsites.map { it.point })
            if (bounds != null) zoomTo(bounds)
        } else if (selectedId != null && selectedId != lastSelected) {
            campsites.firstOrNull { it.campsite.id == selectedId }?.let {
                map.controller.animateTo(GeoPoint(it.point.latitude, it.point.longitude))
            }
        }
        lastSelected = selectedId
    }

    private fun redraw(campsites: List<MapCampsite>, selectedId: String?, center: MapCenter?, radiusKm: Int) {
        map.overlays.clear()
        map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean { onMapClick(); return true }
            override fun longPressHelper(p: GeoPoint?): Boolean = false
        }))
        if (center != null) {
            val point = GeoPoint(center.point.latitude, center.point.longitude)
            map.overlays.add(searchCircle(point, radiusKm))
            map.overlays.add(Marker(map).apply {
                position = point
                icon = BitmapDrawable(map.resources, locationDot(map.context, center.isUserLocation))
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                infoWindow = null
                setOnMarkerClickListener { _, _ -> true }
            })
        }
        // Draw the selected pin last so it sits on top of the others.
        campsites.sortedBy { it.campsite.id == selectedId }.forEach { site ->
            val selected = site.campsite.id == selectedId
            val price = formatPrice(site.campsite.pricePerNight)
            map.overlays.add(Marker(map).apply {
                position = GeoPoint(site.point.latitude, site.point.longitude)
                icon = pinCache.getOrPut(price to selected) {
                    BitmapDrawable(map.resources, priceTag(map.context, price, selected))
                }
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = site.campsite.name
                infoWindow = null
                setOnMarkerClickListener { _, _ -> onPinClick(site.campsite.id); true }
            })
        }
        map.invalidate()
    }

    private fun searchCircle(point: GeoPoint, radiusKm: Int) = Polygon(map).apply {
        points = Polygon.pointsAsCircle(point, radiusKm * 1000.0)
        val density = map.resources.displayMetrics.density
        outlinePaint.apply {
            color = PinedrawColors.Lime.copy(alpha = 0.7f).toArgb()
            strokeWidth = 1.5f * density
            pathEffect = DashPathEffect(floatArrayOf(6f * density, 5f * density), 0f)
            isAntiAlias = true
        }
        fillPaint.color = PinedrawColors.Lime.copy(alpha = 0.07f).toArgb()
        infoWindow = null
        setOnClickListener { _, _, _ -> onMapClick(); true }
    }

    private fun zoomTo(bounds: MapBounds) {
        val box = BoundingBox(bounds.north, bounds.east, bounds.south, bounds.west)
        // zoomToBoundingBox needs the view's size, which is unknown before the first layout.
        if (map.width > 0 && map.height > 0) map.zoomToBoundingBox(box, false)
        else map.addOnFirstLayoutListener { _, _, _, _, _ -> map.zoomToBoundingBox(box, false) }
    }
}

private fun createMapView(context: Context): MapView {
    Configuration.getInstance().apply {
        // OpenStreetMap asks apps to identify themselves; tiles are cached in the app's own storage.
        userAgentValue = MAP_USER_AGENT
        osmdroidBasePath = File(context.cacheDir, "osmdroid")
        osmdroidTileCache = File(osmdroidBasePath, "tiles")
    }
    return MapView(context).apply {
        setTileSource(osmTiles)
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        isTilesScaledToDpi = true
        minZoomLevel = 3.0
        maxZoomLevel = 18.0
        isVerticalMapRepetitionEnabled = false
        setScrollableAreaLimitLatitude(MapView.getTileSystem().maxLatitude, MapView.getTileSystem().minLatitude, 0)
        overlayManager.tilesOverlay.setColorFilter(darkForestTiles())
        overlayManager.tilesOverlay.loadingBackgroundColor = PinedrawColors.Background.toArgb()
        overlayManager.tilesOverlay.loadingLineColor = PinedrawColors.Surface.toArgb()
        controller.setZoom(4.0)
        controller.setCenter(GeoPoint(53.5, -113.5)) // Alberta, until campsites load.
    }
}

// Inverts the light OpenStreetMap tiles and tints them toward the app's forest green, like mockup B04.
private fun darkForestTiles(): ColorMatrixColorFilter {
    val invert = ColorMatrix(floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f
    ))
    val grey = ColorMatrix().apply { setSaturation(0f) }
    val tint = ColorMatrix(floatArrayOf(
        0.42f, 0f, 0f, 0f, 4f,
        0f, 0.55f, 0f, 0f, 14f,
        0f, 0f, 0.45f, 0f, 8f,
        0f, 0f, 0f, 1f, 0f
    ))
    invert.postConcat(grey)
    invert.postConcat(tint)
    return ColorMatrixColorFilter(invert)
}

// A rounded price label with a pointer underneath, like the mockup's pins.
private fun priceTag(context: Context, price: String, selected: Boolean): Bitmap {
    val d = context.resources.displayMetrics.density
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13f * d
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = PinedrawColors.Ink.toArgb()
    }
    val padX = 12f * d
    val bodyH = 26f * d
    val pointer = 6f * d
    val shadow = 2f * d
    val width = text.measureText(price) + padX * 2
    val bitmap = Bitmap.createBitmap((width + shadow * 2).toInt(), (bodyH + pointer + shadow * 2).toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = (if (selected) PinedrawColors.Lime else PinedrawColors.Text).toArgb()
        setShadowLayer(shadow, 0f, shadow / 2, 0x66000000)
    }
    val body = RectF(shadow, shadow, shadow + width, shadow + bodyH)
    canvas.drawRoundRect(body, bodyH / 2, bodyH / 2, fill)
    val mid = shadow + width / 2
    canvas.drawPath(Path().apply {
        moveTo(mid - pointer, body.bottom - 1)
        lineTo(mid + pointer, body.bottom - 1)
        lineTo(mid, body.bottom + pointer)
        close()
    }, fill)
    val baseline = body.centerY() - (text.descent() + text.ascent()) / 2
    canvas.drawText(price, mid - text.measureText(price) / 2, baseline, text)
    return bitmap
}

// A blue dot with a soft halo for the entrant, or a lime dot for a searched place.
private fun locationDot(context: Context, isUser: Boolean): Bitmap {
    val d = context.resources.displayMetrics.density
    val size = (44 * d).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val c = size / 2f
    val color = if (isUser) PinedrawColors.Sky.toArgb() else PinedrawColors.Lime.toArgb()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = color; paint.alpha = 60
    canvas.drawCircle(c, c, 20 * d, paint)
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(c, c, 9 * d, paint)
    paint.color = color
    canvas.drawCircle(c, c, 7 * d, paint)
    return bitmap
}
