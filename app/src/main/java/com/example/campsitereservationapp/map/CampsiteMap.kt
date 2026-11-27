package com.example.campsitereservationapp.map

import com.example.campsitereservationapp.home.CampsiteFilters
import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.model.Lottery
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The rules behind the campsite map (WOW factor): which campsites are inside the chosen
 * distance of the user or a searched place, how far away each one is, and the text the map
 * shows. Nothing here uses Firebase or Android, so it is covered by unit tests.
 *
 * Outstanding issues: distances are straight-line, not driving distances.
 */

/** Distances an entrant can search within, in kilometres. */
val MAP_RADII_KM = listOf(10, 25, 50, 100, 250)

/** Distance used until the entrant picks another one. */
const val DEFAULT_MAP_RADIUS_KM = 25

private const val EARTH_RADIUS_KM = 6371.0
private const val KM_PER_DEGREE = 111.32

/**
 * A spot on the map.
 *
 * @property latitude Degrees north, from -90 to 90.
 * @property longitude Degrees east, from -180 to 180.
 */
data class MapPoint(val latitude: Double, val longitude: Double)

/**
 * The place campsites are measured from: the entrant's location or a place they searched for.
 *
 * @property point Where it is.
 * @property label Name shown in "Within 25 km of …"; ignored for the entrant's location.
 * @property isUserLocation True when it is where the entrant is.
 */
data class MapCenter(val point: MapPoint, val label: String, val isUserLocation: Boolean = false)

/**
 * A campsite shown on the map.
 *
 * @property campsite The campsite.
 * @property point Where its pin goes.
 * @property distanceKm How far it is from the map's center, or null with no center.
 */
data class MapCampsite(val campsite: Campsite, val point: MapPoint, val distanceKm: Double?)

/**
 * The edges of the area the map should show.
 */
data class MapBounds(val north: Double, val south: Double, val east: Double, val west: Double)

/**
 * Everything the map screen remembers while the entrant opens a campsite and comes back.
 *
 * @property query Text in the search box.
 * @property center Place campsites are measured from, or null to show every campsite.
 * @property radiusKm Chosen search distance.
 * @property selectedId Campsite whose card is shown, or null.
 * @property showingList True while the list of campsites is open.
 * @property filters Guests, dates and location filters, shared with Discover's filter sheet.
 * @property askedForLocation True once the map has asked for the entrant's location.
 */
data class CampsiteMapState(
    val query: String = "",
    val center: MapCenter? = null,
    val radiusKm: Int = DEFAULT_MAP_RADIUS_KM,
    val selectedId: String? = null,
    val showingList: Boolean = false,
    val filters: CampsiteFilters = CampsiteFilters(),
    val askedForLocation: Boolean = false
)

/**
 * Straight-line distance between two points along the Earth's surface (haversine formula).
 *
 * @return The distance in kilometres.
 */
fun distanceKm(from: MapPoint, to: MapPoint): Double {
    val lat1 = Math.toRadians(from.latitude)
    val lat2 = Math.toRadians(to.latitude)
    val dLat = lat2 - lat1
    val dLon = Math.toRadians(to.longitude - from.longitude)
    val a = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * EARTH_RADIUS_KM * asin(sqrt(a.coerceIn(0.0, 1.0)))
}

/**
 * Where a campsite's pin goes, or null if its coordinates are missing or invalid.
 *
 * Exactly 0, 0 is treated as missing, because that is what an unset location is saved as.
 */
fun Campsite.mapPoint(): MapPoint? {
    val valid = latitude.isFinite() && longitude.isFinite() &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0 && !(latitude == 0.0 && longitude == 0.0)
    return if (valid) MapPoint(latitude, longitude) else null
}

/**
 * Picks the campsites to pin on the map.
 *
 * With no center every campsite with a location is shown, by name. With a center only those
 * within [radiusKm] are shown, closest first.
 *
 * @param campsites Published campsites.
 * @param center Place to measure from, or null.
 * @param radiusKm Search distance in kilometres.
 */
fun campsitesOnMap(campsites: List<Campsite>, center: MapPoint?, radiusKm: Int): List<MapCampsite> {
    val pinned = campsites.mapNotNull { site ->
        site.mapPoint()?.let { MapCampsite(site, it, center?.let { c -> distanceKm(c, it) }) }
    }
    return if (center == null) pinned.sortedBy { it.campsite.name.lowercase() }
    else pinned.filter { it.distanceKm!! <= radiusKm }.sortedBy { it.distanceKm }
}

/**
 * The closest campsite to a point, used to suggest one when none are within the distance.
 *
 * @return The closest campsite with a location, or null if there are none.
 */
fun nearestCampsite(campsites: List<Campsite>, center: MapPoint): MapCampsite? =
    campsites.mapNotNull { site -> site.mapPoint()?.let { MapCampsite(site, it, distanceKm(center, it)) } }
        .minByOrNull { it.distanceKm!! }

/**
 * Formats a distance the way the map shows it: metres under 1 km, one decimal under 10 km,
 * then whole kilometres.
 */
fun formatDistance(km: Double): String = when {
    km < 1 -> "${(km * 1000).roundToInt().coerceAtLeast(1)} m"
    km < 10 -> String.format(Locale.CANADA, "%.1f km", km)
    else -> "${km.roundToInt()} km"
}

/**
 * Text on the distance pill, such as "Within 25 km of you".
 *
 * @param center Place campsites are measured from, or null when every campsite is shown.
 * @param radiusKm Search distance in kilometres.
 */
fun radiusLabel(center: MapCenter?, radiusKm: Int): String = when {
    center == null -> "All campsites"
    center.isUserLocation -> "Within $radiusKm km of you"
    else -> "Within $radiusKm km of ${center.label}"
}

/**
 * Heading above the campsite list, such as "3 campsites within 25 km of you".
 */
fun mapListTitle(count: Int, center: MapCenter?, radiusKm: Int): String {
    val campsites = "$count campsite${if (count == 1) "" else "s"}"
    return when {
        center == null -> "$campsites on the map"
        center.isUserLocation -> "$campsites within $radiusKm km of you"
        else -> "$campsites within $radiusKm km of ${center.label}"
    }
}

/**
 * Message shown when no campsites are inside the distance, pointing to the closest one.
 *
 * @param center Place campsites are measured from.
 * @param radiusKm Search distance in kilometres.
 * @param nearest Closest campsite anywhere, or null if no campsite has a location.
 */
fun emptyAreaMessage(center: MapCenter, radiusKm: Int, nearest: MapCampsite?): String {
    val place = if (center.isUserLocation) "you" else center.label
    val start = "No campsites within $radiusKm km of $place."
    if (nearest == null) return start
    val name = nearest.campsite.name.ifBlank { "Unnamed campsite" }
    return "$start The closest is $name, ${formatDistance(nearest.distanceKm!!)} away."
}

/**
 * Finds a place by matching the search against campsite locations and names, used when the
 * phone's address lookup is unavailable. The center is the middle of the matching campsites.
 *
 * @return The place, or null if no campsite matches.
 */
fun placeFromCampsites(query: String, campsites: List<Campsite>): MapCenter? {
    val text = query.trim()
    if (text.isBlank()) return null
    val points = campsites.filter {
        it.location.contains(text, ignoreCase = true) || it.name.contains(text, ignoreCase = true)
    }.mapNotNull { it.mapPoint() }
    if (points.isEmpty()) return null
    return MapCenter(MapPoint(points.map { it.latitude }.average(), points.map { it.longitude }.average()), text)
}

/**
 * The area to show for a search: the circle of [radiusKm] around [center], with a little room.
 */
fun areaBounds(center: MapPoint, radiusKm: Int): MapBounds {
    val padded = radiusKm * 1.15
    val latDelta = padded / KM_PER_DEGREE
    val lonDelta = padded / (KM_PER_DEGREE * max(cos(Math.toRadians(center.latitude)), 0.01))
    return MapBounds(
        north = (center.latitude + latDelta).coerceAtMost(85.0),
        south = (center.latitude - latDelta).coerceAtLeast(-85.0),
        east = (center.longitude + lonDelta).coerceAtMost(180.0),
        west = (center.longitude - lonDelta).coerceAtLeast(-180.0)
    )
}

/**
 * The area to show so every pin fits. A single pin, or pins very close together, get about
 * 10 km around them so the map does not zoom in all the way.
 *
 * @return The area, or null if there are no points.
 */
fun pointsBounds(points: List<MapPoint>): MapBounds? {
    if (points.isEmpty()) return null
    val north = points.maxOf { it.latitude }
    val south = points.minOf { it.latitude }
    val east = points.maxOf { it.longitude }
    val west = points.minOf { it.longitude }
    val latPad = max((north - south) * 0.15, 0.1)
    val lonPad = max((east - west) * 0.15, 0.1)
    return MapBounds(
        (north + latPad).coerceAtMost(85.0), (south - latPad).coerceAtLeast(-85.0),
        (east + lonPad).coerceAtMost(180.0), (west - lonPad).coerceAtLeast(-180.0)
    )
}

/**
 * "Sleeps 8" for a campsite's card, or null when it has no limit.
 */
fun sleepsText(maxOccupancy: Int?): String? = maxOccupancy?.takeIf { it > 0 }?.let { "Sleeps $it" }

/**
 * Text on the card's lottery chip, such as "Lottery open · 3d left".
 *
 * @param lottery The campsite's open lottery, or null if it has none.
 * @param today Today's date as yyyy-MM-dd.
 * @return The chip text, or null when no lottery is taking entries.
 */
fun lotteryChipText(lottery: Lottery?, today: String): String? {
    if (lottery == null || lottery.drawCompleted) return null
    if (today < lottery.registrationStartDate || today > lottery.registrationEndDate) return null
    val days = daysBetween(today, lottery.registrationEndDate) ?: return "Lottery open"
    return if (days == 0) "Lottery open · closes today" else "Lottery open · ${days}d left"
}

private fun daysBetween(from: String, to: String): Int? = try {
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
        isLenient = false
    }
    val start = format.parse(from) ?: return null
    val end = format.parse(to) ?: return null
    ((end.time - start.time) / 86_400_000L).toInt()
} catch (_: Exception) {
    null
}
