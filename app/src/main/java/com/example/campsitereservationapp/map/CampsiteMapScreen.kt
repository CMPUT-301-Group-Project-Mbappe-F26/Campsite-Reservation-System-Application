package com.example.campsitereservationapp.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.campsitereservationapp.campsite.CampsitePhoto
import com.example.campsitereservationapp.home.CampsiteFilterDialog
import com.example.campsitereservationapp.home.applyCampsiteFilters
import com.example.campsitereservationapp.home.hasActiveFilters
import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.model.reservationDateToday
import com.example.campsitereservationapp.service.CampsiteService
import com.example.campsitereservationapp.ui.components.ButtonStyle
import com.example.campsitereservationapp.ui.components.CircleIconButton
import com.example.campsitereservationapp.ui.components.PinedrawButton
import com.example.campsitereservationapp.ui.components.PinedrawTextField
import com.example.campsitereservationapp.ui.components.StatusChip
import com.example.campsitereservationapp.ui.components.formatPrice
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import com.example.campsitereservationapp.ui.theme.PinedrawRadius
import com.example.campsitereservationapp.ui.theme.PinedrawSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/*
 * The campsite map (WOW factor, following mockup B04): every published campsite as a price
 * pin, a search box that moves the map to a town or park, and a distance the entrant picks
 * around themselves or the searched place. Tapping a pin shows the campsite's card, and
 * View opens its details.
 *
 * [CampsiteMapScreen] loads campsites, looks up places and the entrant's location.
 * [CampsiteMapContent] only draws the screen, with the map passed in, so intent tests can
 * check it without Firebase, GPS or map tiles.
 *
 * Outstanding issues: place search uses the phone's built-in address lookup, which needs an
 * internet connection; without it, searches only match campsite names and locations.
 */

/**
 * Loads the campsite map for an entrant.
 *
 * @param state What the map remembers; kept by the caller so it survives opening a campsite.
 * @param onStateChange Saves a changed [state].
 * @param onBack Returns to Discover.
 * @param onCampsiteClick Opens a campsite's details.
 */
@Composable
fun CampsiteMapScreen(
    state: CampsiteMapState,
    onStateChange: (CampsiteMapState) -> Unit,
    onBack: () -> Unit,
    onCampsiteClick: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val campsiteService = remember { CampsiteService() }
    var campsites by remember { mutableStateOf<List<Campsite>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var searching by remember { mutableStateOf(false) }
    var locating by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var showFilters by remember { mutableStateOf(false) }
    var lotteryText by remember { mutableStateOf<String?>(null) }
    val latest = rememberUpdatedStateHolder(state)

    DisposableEffect(campsiteService) {
        val listener = campsiteService.listenToPublishedCampsites(
            onUpdate = { campsites = it; loading = false },
            onError = { message = "Could not load campsites."; loading = false }
        )
        onDispose { listener.remove() }
    }

    fun locate() {
        locating = true
        message = ""
        scope.launch {
            val point = try {
                withTimeoutOrNull(15000) { currentLocation(context) }
            } catch (_: Exception) {
                coroutineContext.ensureActive()
                null
            }
            locating = false
            if (point == null) message = "Couldn't find your location. Check that location is turned on."
            else onStateChange(latest.value.copy(center = MapCenter(point, "you", isUserLocation = true), query = "", selectedId = null))
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) locate()
        else message = "Allow location access to see campsites near you, or search for a place."
    }

    fun requestLocation() {
        if (hasLocationPermission(context)) locate()
        else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    // The team's design centers the map on the entrant, so ask once when the map first opens.
    LaunchedEffect(Unit) {
        if (!state.askedForLocation && state.center == null) {
            onStateChange(state.copy(askedForLocation = true))
            requestLocation()
        }
    }

    LaunchedEffect(state.selectedId) {
        lotteryText = null
        val id = state.selectedId ?: return@LaunchedEffect
        lotteryText = try {
            lotteryChipText(campsiteService.getOpenLottery(id), reservationDateToday())
        } catch (_: Exception) {
            coroutineContext.ensureActive()
            null
        }
    }

    fun search() {
        val query = state.query.trim()
        message = ""
        if (query.isBlank()) {
            onStateChange(state.copy(center = null, selectedId = null))
            return
        }
        searching = true
        scope.launch {
            val place = try {
                withTimeoutOrNull(15000) { findPlace(context, query) }
            } catch (_: Exception) {
                coroutineContext.ensureActive()
                null
            } ?: placeFromCampsites(query, campsites)
            searching = false
            if (place == null) message = "Couldn't find \"$query\". Try a town, park or address."
            else onStateChange(latest.value.copy(center = place, selectedId = null, showingList = false))
        }
    }

    if (showFilters) {
        CampsiteFilterDialog(
            currentFilters = state.filters,
            onApply = { onStateChange(state.copy(filters = it, selectedId = null)); showFilters = false },
            onClose = { showFilters = false }
        )
    }

    val shown = campsitesOnMap(applyCampsiteFilters(campsites, state.filters), state.center?.point, state.radiusKm)
    val emptyMessage = state.center?.takeIf { !loading && shown.isEmpty() }
        ?.let { emptyAreaMessage(it, state.radiusKm, nearestCampsite(applyCampsiteFilters(campsites, state.filters), it.point)) }

    CampsiteMapContent(
        state = state,
        campsites = shown,
        loading = loading,
        searching = searching,
        locating = locating,
        message = message.ifBlank { emptyMessage.orEmpty() },
        lotteryText = lotteryText,
        onStateChange = onStateChange,
        onSearch = ::search,
        onLocate = ::requestLocation,
        onFilterClick = { showFilters = true },
        onBack = onBack,
        onOpen = onCampsiteClick,
        map = { modifier ->
            OsmCampsiteMap(
                campsites = shown,
                selectedId = state.selectedId,
                center = state.center,
                radiusKm = state.radiusKm,
                onPinClick = { onStateChange(latest.value.copy(selectedId = it, showingList = false)) },
                onMapClick = { if (latest.value.selectedId != null) onStateChange(latest.value.copy(selectedId = null)) },
                modifier = modifier
            )
        }
    )
}

/**
 * Draws the map screen: the map, the search bar, the distance pill, the List and location
 * buttons, and the selected campsite's card or the campsite list.
 *
 * @param state What the map shows.
 * @param campsites Campsites on the map, closest first when there is a center.
 * @param loading True until campsites have loaded.
 * @param searching True while a place is being looked up.
 * @param locating True while finding the entrant's location.
 * @param message Problem or "no campsites nearby" message, or blank.
 * @param lotteryText Lottery chip for the selected campsite, or null.
 * @param onStateChange Saves a change made on this screen.
 * @param onSearch Looks up [CampsiteMapState.query].
 * @param onLocate Centers the map on the entrant.
 * @param onFilterClick Opens the filter sheet.
 * @param onBack Returns to Discover.
 * @param onOpen Opens a campsite's details.
 * @param map Draws the map in the given space.
 */
@Composable
fun CampsiteMapContent(
    state: CampsiteMapState,
    campsites: List<MapCampsite>,
    loading: Boolean,
    searching: Boolean,
    locating: Boolean,
    message: String,
    lotteryText: String?,
    onStateChange: (CampsiteMapState) -> Unit,
    onSearch: () -> Unit,
    onLocate: () -> Unit,
    onFilterClick: () -> Unit,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    map: @Composable (Modifier) -> Unit
) {
    val selected = campsites.firstOrNull { it.campsite.id == state.selectedId }
    Box(Modifier.fillMaxSize().background(PinedrawColors.Background)) {
        map(Modifier.fillMaxSize())

        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = PinedrawSpacing.Large, vertical = PinedrawSpacing.Small),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PinedrawSpacing.Medium)
        ) {
            MapSearchBar(state, searching, onStateChange, onSearch, onFilterClick, onBack)
            RadiusPill(state, onStateChange)
            if (message.isNotBlank()) MapMessage(message)
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                .padding(horizontal = PinedrawSpacing.Large, vertical = PinedrawSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(PinedrawSpacing.Medium)
        ) {
            Box(Modifier.fillMaxWidth()) {
                MapPillButton(
                    text = if (state.showingList) "Map" else "List",
                    icon = if (state.showingList) Icons.Rounded.Map else Icons.AutoMirrored.Rounded.List,
                    onClick = { onStateChange(state.copy(showingList = !state.showingList, selectedId = null)) },
                    modifier = Modifier.align(Alignment.Center)
                )
                Box(Modifier.align(Alignment.CenterEnd)) {
                    if (locating) {
                        Box(Modifier.size(48.dp).clip(PinedrawRadius.Pill).background(PinedrawColors.Surface), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = PinedrawColors.Lime, strokeWidth = 2.dp)
                        }
                    } else {
                        CircleIconButton(Icons.Rounded.MyLocation, "Show my location", onLocate, container = PinedrawColors.Surface)
                    }
                }
            }
            when {
                state.showingList -> CampsiteMapList(state, campsites, loading, onStateChange)
                selected != null -> MapCampsiteCard(selected, lotteryText, onOpen = { onOpen(selected.campsite.id) })
            }
        }
    }
}

@Composable
private fun MapSearchBar(
    state: CampsiteMapState,
    searching: Boolean,
    onStateChange: (CampsiteMapState) -> Unit,
    onSearch: () -> Unit,
    onFilterClick: () -> Unit,
    onBack: () -> Unit
) {
    val focus = LocalFocusManager.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PinedrawSpacing.Small)) {
        CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack, container = PinedrawColors.Surface)
        Box(Modifier.weight(1f).clip(PinedrawRadius.Medium).background(PinedrawColors.Surface)) {
            PinedrawTextField(
                value = state.query,
                onValueChange = { onStateChange(state.copy(query = it)) },
                placeholder = "Search a place",
                leadingIcon = Icons.Rounded.Search,
                trailingIcon = when {
                    searching -> { { CircularProgressIndicator(Modifier.size(20.dp), color = PinedrawColors.Lime, strokeWidth = 2.dp) } }
                    state.query.isNotEmpty() || (state.center != null && !state.center.isUserLocation) -> {
                        {
                            IconButton(onClick = {
                                val clearedCenter = state.center?.takeIf { it.isUserLocation }
                                onStateChange(state.copy(query = "", center = clearedCenter, selectedId = null))
                            }) { Icon(Icons.Rounded.Close, "Clear search") }
                        }
                    }
                    else -> null
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); onSearch() })
            )
        }
        CircleIconButton(
            Icons.Rounded.Tune, "Filter campsites", onFilterClick,
            tint = if (hasActiveFilters(state.filters)) PinedrawColors.Ink else PinedrawColors.Text,
            container = if (hasActiveFilters(state.filters)) PinedrawColors.Lime else PinedrawColors.Surface
        )
    }
}

@Composable
private fun RadiusPill(state: CampsiteMapState, onStateChange: (CampsiteMapState) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(PinedrawRadius.Pill).background(PinedrawColors.Surface)
                .border(1.dp, PinedrawColors.Border, PinedrawRadius.Pill)
                .clickable { open = true }
                .semantics { contentDescription = "Search distance" }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Rounded.MyLocation, null, tint = PinedrawColors.Lime, modifier = Modifier.size(16.dp))
            Text(radiusLabel(state.center, state.radiusKm), style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold, color = PinedrawColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = PinedrawColors.Text, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = PinedrawColors.SurfaceRaised) {
            MAP_RADII_KM.forEach { km ->
                DropdownMenuItem(
                    text = {
                        Text("Within $km km", color = if (km == state.radiusKm) PinedrawColors.Lime else PinedrawColors.Text,
                            fontWeight = if (km == state.radiusKm) FontWeight.Bold else FontWeight.Normal)
                    },
                    onClick = { open = false; onStateChange(state.copy(radiusKm = km, selectedId = null)) }
                )
            }
        }
    }
}

@Composable
private fun MapMessage(text: String) {
    Text(
        text,
        Modifier.clip(PinedrawRadius.Medium).background(PinedrawColors.Surface.copy(alpha = 0.95f))
            .border(1.dp, PinedrawColors.Border, PinedrawRadius.Medium).padding(horizontal = 14.dp, vertical = 10.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = PinedrawColors.TextSecondary
    )
}

@Composable
private fun MapPillButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(PinedrawRadius.Pill).background(PinedrawColors.Text).clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, tint = PinedrawColors.Ink, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = PinedrawColors.Ink)
    }
}

/**
 * The selected campsite's card from mockup B04: photo, name, location and distance, sleeps,
 * rating, lottery status, price and View.
 */
@Composable
internal fun MapCampsiteCard(site: MapCampsite, lotteryText: String?, onOpen: () -> Unit) {
    val campsite = site.campsite
    Row(
        Modifier.fillMaxWidth().clip(PinedrawRadius.Large).background(PinedrawColors.Surface.copy(alpha = 0.97f))
            .border(1.dp, PinedrawColors.Border, PinedrawRadius.Large).clickable(onClick = onOpen).padding(PinedrawSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CampsitePhoto(campsite.imageUrls.firstOrNull(), Modifier.size(width = 112.dp, height = 136.dp), seed = campsite.id, shape = PinedrawRadius.Medium)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(campsite.name.ifBlank { "Unnamed campsite" }, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = PinedrawColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconLine(Icons.Rounded.Place, listOfNotNull(campsite.location.takeIf { it.isNotBlank() }, site.distanceKm?.let(::formatDistance)).joinToString(" · ").ifBlank { "Location not listed" })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                sleepsText(campsite.maxOccupancy)?.let { IconLine(Icons.Rounded.Groups, it) }
                RatingText(campsite)
            }
            lotteryText?.let { StatusChip(it, color = PinedrawColors.Lime) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatPrice(campsite.pricePerNight), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = PinedrawColors.Lime)
                Text(" /night", style = MaterialTheme.typography.bodySmall, color = PinedrawColors.TextSecondary, modifier = Modifier.weight(1f))
                PinedrawButton("View", onClick = onOpen, compact = true, style = ButtonStyle.Primary)
            }
        }
    }
}

@Composable
private fun CampsiteMapList(
    state: CampsiteMapState,
    campsites: List<MapCampsite>,
    loading: Boolean,
    onStateChange: (CampsiteMapState) -> Unit
) {
    Column(
        Modifier.fillMaxWidth().heightIn(max = 340.dp).clip(PinedrawRadius.Large).background(PinedrawColors.Surface.copy(alpha = 0.97f))
            .border(1.dp, PinedrawColors.Border, PinedrawRadius.Large).padding(PinedrawSpacing.Medium),
        verticalArrangement = Arrangement.spacedBy(PinedrawSpacing.Small)
    ) {
        Text(mapListTitle(campsites.size, state.center, state.radiusKm), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = PinedrawColors.Text)
        when {
            loading -> Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PinedrawColors.Lime, strokeWidth = 3.dp)
            }
            campsites.isEmpty() -> Text("Try a larger distance or search somewhere else.",
                style = MaterialTheme.typography.bodyMedium, color = PinedrawColors.TextSecondary)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(PinedrawSpacing.Small), contentPadding = PaddingValues(bottom = 4.dp)) {
                items(campsites, key = { it.campsite.id }) { site ->
                    MapListRow(site, onClick = { onStateChange(state.copy(selectedId = site.campsite.id, showingList = false)) })
                }
            }
        }
    }
}

@Composable
private fun MapListRow(site: MapCampsite, onClick: () -> Unit) {
    val campsite = site.campsite
    Row(
        Modifier.fillMaxWidth().clip(PinedrawRadius.Medium).background(PinedrawColors.Glass)
            .border(1.dp, PinedrawColors.Border, PinedrawRadius.Medium).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CampsitePhoto(campsite.imageUrls.firstOrNull(), Modifier.size(56.dp), seed = campsite.id, shape = PinedrawRadius.Small)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(campsite.name.ifBlank { "Unnamed campsite" }, style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold, color = PinedrawColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(site.distanceKm?.let { "${formatDistance(it)} away" }, campsite.location.takeIf { it.isNotBlank() }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = PinedrawColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatPrice(campsite.pricePerNight), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = PinedrawColors.Lime)
        Text(" /nt", style = MaterialTheme.typography.bodySmall, color = PinedrawColors.TextSecondary)
    }
}

@Composable
private fun IconLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, null, tint = PinedrawColors.TextSecondary, modifier = Modifier.size(15.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = PinedrawColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RatingText(campsite: Campsite) {
    val rating = campsite.averageRating ?: return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(Icons.Rounded.Star, null, tint = PinedrawColors.Amber, modifier = Modifier.size(15.dp))
        Text(String.format(Locale.CANADA, "%.1f", rating), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = PinedrawColors.Text)
        if (campsite.ratingCount > 0) Text("(${campsite.ratingCount})", style = MaterialTheme.typography.bodySmall, color = PinedrawColors.TextMuted)
    }
}

// Lets callbacks that finish later read the newest state instead of the one they started with.
@Composable
private fun rememberUpdatedStateHolder(state: CampsiteMapState): StateBox {
    val box = remember { StateBox(state) }
    box.value = state
    return box
}

private class StateBox(var value: CampsiteMapState)

private fun hasLocationPermission(context: Context): Boolean =
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

// Asks the phone for a fresh location, falling back to the most recent one it knows.
@SuppressLint("MissingPermission") // Only called after hasLocationPermission.
private suspend fun currentLocation(context: Context): MapPoint? {
    if (!hasLocationPermission(context)) return null
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter {
        runCatching { manager.isProviderEnabled(it) }.getOrDefault(false)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        for (provider in providers) {
            val fresh = withTimeoutOrNull(8000) {
                suspendCancellableCoroutine<Location?> { continuation ->
                    manager.getCurrentLocation(provider, null, ContextCompat.getMainExecutor(context)) { continuation.resume(it) }
                }
            }
            if (fresh != null) return MapPoint(fresh.latitude, fresh.longitude)
        }
    }
    val last = providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
    return last?.let { MapPoint(it.latitude, it.longitude) }
}

// Looks up a typed place with the phone's address lookup, the same one Create Campsite uses.
private suspend fun findPlace(context: Context, query: String): MapCenter? = withContext(Dispatchers.IO) {
    if (!Geocoder.isPresent()) return@withContext null
    @Suppress("DEPRECATION")
    val address = Geocoder(context, Locale.getDefault()).getFromLocationName(query, 1)?.firstOrNull() ?: return@withContext null
    val name = listOfNotNull(address.locality ?: address.featureName, address.adminArea)
        .filter { it.isNotBlank() }.distinct().joinToString(", ").ifBlank { query }
    MapCenter(MapPoint(address.latitude, address.longitude), name)
}
