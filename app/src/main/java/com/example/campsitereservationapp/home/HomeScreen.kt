package com.example.campsitereservationapp.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.campsite.CampsitePhoto
import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.model.User
import com.example.campsitereservationapp.service.CampsiteService
import com.example.campsitereservationapp.ui.components.EmptyState
import com.example.campsitereservationapp.ui.components.ErrorState
import com.example.campsitereservationapp.ui.components.formatPrice
import com.example.campsitereservationapp.ui.components.InitialsAvatar
import com.example.campsitereservationapp.ui.components.LoadingState
import com.example.campsitereservationapp.ui.components.PinedrawBackground
import com.example.campsitereservationapp.ui.components.PinedrawTextField
import com.example.campsitereservationapp.ui.components.SectionHeader
import com.example.campsitereservationapp.ui.components.StatusChip
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import com.example.campsitereservationapp.ui.theme.PinedrawRadius
import com.example.campsitereservationapp.ui.theme.PinedrawSpacing
import java.util.Calendar
import java.util.Locale

/**
 * Displays the Discover screen where users can browse and search campsites.
 *
 * Loads published campsites from Firebase and shows their basic information.
 * Users can search by name or location, filter the list and open a campsite.
 *
 * @param user The currently signed-in user.
 * @param onProfileClick Opens the user's profile.
 * @param onCampsiteClick Opens the selected campsite using its ID.
 * @param searchText The current text entered in the search bar.
 * @param onSearchTextChange Updates the search text when the user types.
 */
@Composable
fun HomeScreen(
    user: User,
    onProfileClick: () -> Unit,
    onCampsiteClick: (String) -> Unit,
    searchText: String,
    onSearchTextChange: (String) -> Unit
) {
    val campsiteService = remember { CampsiteService() }
    var selectedFilters by remember { mutableStateOf(CampsiteFilters()) }
    var showFilters by remember { mutableStateOf(false) }
    var campsites by remember { mutableStateOf<List<Campsite>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var retry by remember { mutableStateOf(0) }
    val searchedCampsites = getDisplayedCampsites(campsites, searchText)
    val filteredCampsites = applyCampsiteFilters(searchedCampsites, selectedFilters)
    val browsing = searchText.isBlank() && !hasActiveFilters(selectedFilters)

    DisposableEffect(campsiteService, retry) {
        val listener = campsiteService.listenToPublishedCampsites(
            onUpdate = { updated ->
                campsites = updated
                error = ""
                loading = false
            },
            onError = {
                error = "Could not load campsites."
                loading = false
            }
        )
        onDispose { listener.remove() }
    }

    if (showFilters) {
        CampsiteFilterDialog(
            currentFilters = selectedFilters,
            onApply = {
                selectedFilters = it
                showFilters = false
            },
            onClose = { showFilters = false }
        )
    }

    PinedrawBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(bottom = PinedrawSpacing.Screen),
            verticalArrangement = Arrangement.spacedBy(PinedrawSpacing.Large)
        ) {
            item {
                Column(
                    Modifier.padding(horizontal = PinedrawSpacing.Gutter).padding(top = PinedrawSpacing.Large),
                    verticalArrangement = Arrangement.spacedBy(PinedrawSpacing.Gutter)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        InitialsAvatar(
                            user.name.ifBlank { "Explorer" },
                            size = 48.dp,
                            modifier = Modifier.clip(CircleShape).clickable(onClick = onProfileClick)
                        )
                        Spacer(Modifier.width(PinedrawSpacing.Medium))
                        Column(Modifier.weight(1f)) {
                            Text(greeting(), style = MaterialTheme.typography.bodyMedium, color = PinedrawColors.TextSecondary)
                            Text(
                                user.name.ifBlank { "Explorer" },
                                style = MaterialTheme.typography.titleLarge,
                                color = PinedrawColors.Text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Text(
                        buildAnnotatedString {
                            append("Find your ")
                            withStyle(SpanStyle(color = PinedrawColors.Lime)) { append("wild escape") }
                        },
                        style = MaterialTheme.typography.headlineLarge,
                        color = PinedrawColors.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    SearchBar(
                        value = searchText,
                        onValueChange = onSearchTextChange,
                        filtersActive = hasActiveFilters(selectedFilters),
                        onFilterClick = { showFilters = true }
                    )
                    if (hasActiveFilters(selectedFilters)) {
                        ActiveFilters(selectedFilters, onClear = { selectedFilters = CampsiteFilters() })
                    }
                }
            }

            when {
                loading -> item { LoadingState() }
                error.isNotEmpty() -> item { ErrorState(error, onRetry = { loading = true; retry++ }) }
                filteredCampsites.isEmpty() -> item {
                    EmptyState(
                        icon = Icons.Rounded.Search,
                        title = if (browsing) "No campsites available" else "No campsites found",
                        message = if (browsing) "New campsites will appear here as soon as hosts publish them."
                        else "Try a different search or clear your filters."
                    )
                }
                else -> {
                    if (browsing) {
                        val featured = filteredCampsites.sortedByDescending { it.averageRating ?: 0.0 }.take(5)
                        item { SectionHeader("Featured for you", Modifier.padding(horizontal = PinedrawSpacing.Gutter)) }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = PinedrawSpacing.Gutter),
                                horizontalArrangement = Arrangement.spacedBy(PinedrawSpacing.Medium)
                            ) {
                                items(featured, key = { "featured-${it.id}" }) { campsite ->
                                    FeaturedCampsiteCard(campsite, onClick = { onCampsiteClick(campsite.id) })
                                }
                            }
                        }
                    }
                    item {
                        SectionHeader(
                            if (browsing) "All campsites" else "${filteredCampsites.size} ${if (filteredCampsites.size == 1) "result" else "results"}",
                            Modifier.padding(horizontal = PinedrawSpacing.Gutter)
                        )
                    }
                    items(filteredCampsites, key = { it.id }) { campsite ->
                        CampsiteRow(
                            campsite,
                            onClick = { onCampsiteClick(campsite.id) },
                            modifier = Modifier.padding(horizontal = PinedrawSpacing.Gutter)
                        )
                    }
                }
            }
        }
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning,"
    in 12..17 -> "Good afternoon,"
    else -> "Good evening,"
}

@Composable
private fun SearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    filtersActive: Boolean,
    onFilterClick: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PinedrawTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = "Search campsites or places",
            leadingIcon = Icons.Rounded.Search,
            trailingIcon = if (value.isNotEmpty()) {
                { IconButton(onClick = { onValueChange("") }) { Icon(Icons.Rounded.Close, "Clear search") } }
            } else null,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(PinedrawSpacing.Small))
        Box(
            Modifier
                .size(56.dp)
                .clip(PinedrawRadius.Medium)
                .background(if (filtersActive) PinedrawColors.Lime else PinedrawColors.Glass)
                .border(1.dp, if (filtersActive) Color.Transparent else PinedrawColors.Border, PinedrawRadius.Medium)
                .clickable(onClick = onFilterClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Tune,
                contentDescription = "Filter campsites",
                tint = if (filtersActive) PinedrawColors.Ink else PinedrawColors.Text
            )
        }
    }
}

@Composable
private fun ActiveFilters(filters: CampsiteFilters, onClear: () -> Unit) {
    val labels = buildList {
        if (filters.location.isNotBlank()) add(filters.location)
        if (filters.startDate.isNotBlank()) add("${filters.startDate} → ${filters.endDate}")
        if (filters.guests > 0) add("${filters.guests}+ guests")
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            labels.forEach { StatusChip(it, color = PinedrawColors.Lime, showDot = false) }
        }
        Text(
            "Clear",
            style = MaterialTheme.typography.labelLarge,
            color = PinedrawColors.Lime,
            modifier = Modifier.clip(PinedrawRadius.Pill).clickable(onClick = onClear).padding(8.dp)
        )
    }
}

@Composable
private fun RatingLabel(campsite: Campsite, color: Color = PinedrawColors.Text) {
    val rating = campsite.averageRating
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Star, null, tint = PinedrawColors.Amber, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(3.dp))
        Text(
            when {
                rating == null -> "New"
                campsite.ratingCount > 0 -> String.format(Locale.CANADA, "%.1f", rating) + " (${campsite.ratingCount})"
                else -> String.format(Locale.CANADA, "%.1f", rating)
            },
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
    }
}

@Composable
private fun MetaLine(campsite: Campsite, color: Color = PinedrawColors.TextSecondary) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Place, null, tint = color, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(3.dp))
        Text(
            campsite.location.ifBlank { "Alberta" },
            style = MaterialTheme.typography.bodySmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        campsite.maxOccupancy?.let {
            Text("  ·  ", style = MaterialTheme.typography.bodySmall, color = color)
            Icon(Icons.Rounded.Groups, null, tint = color, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(3.dp))
            Text("Sleeps $it", style = MaterialTheme.typography.bodySmall, color = color, maxLines = 1)
        }
    }
}

private fun priceText(campsite: Campsite) = formatPrice(campsite.pricePerNight)

// Large photo card used in the Featured carousel.
@Composable
private fun FeaturedCampsiteCard(campsite: Campsite, onClick: () -> Unit) {
    Box(
        Modifier
            .width(300.dp)
            .height(220.dp)
            .clip(PinedrawRadius.Large)
            .border(1.dp, PinedrawColors.Border, PinedrawRadius.Large)
            .clickable(onClick = onClick)
    ) {
        CampsitePhoto(campsite.imageUrls.firstOrNull(), Modifier.matchParentSize(), seed = campsite.id, shape = null)
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0.35f to Color.Transparent, 1f to PinedrawColors.Background.copy(alpha = 0.92f))
            )
        )
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(PinedrawSpacing.Large)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    campsite.name.ifBlank { "Unnamed campsite" },
                    style = MaterialTheme.typography.titleLarge,
                    color = PinedrawColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(priceText(campsite), style = MaterialTheme.typography.titleLarge, color = PinedrawColors.Lime, maxLines = 1)
                Text(" /night", style = MaterialTheme.typography.bodySmall, color = PinedrawColors.TextSecondary)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { MetaLine(campsite, PinedrawColors.Text.copy(alpha = 0.85f)) }
                Spacer(Modifier.width(PinedrawSpacing.Small))
                RatingLabel(campsite)
            }
        }
    }
}

// Compact row used in the full campsite list.
@Composable
private fun CampsiteRow(campsite: Campsite, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(PinedrawRadius.Large)
            .background(PinedrawColors.Glass)
            .border(1.dp, PinedrawColors.Border, PinedrawRadius.Large)
            .clickable(onClick = onClick)
            .padding(PinedrawSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CampsitePhoto(campsite.imageUrls.firstOrNull(), Modifier.size(88.dp), seed = campsite.id, shape = PinedrawRadius.Medium)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    campsite.name.ifBlank { "Unnamed campsite" },
                    style = MaterialTheme.typography.titleMedium,
                    color = PinedrawColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(priceText(campsite), style = MaterialTheme.typography.titleMedium, color = PinedrawColors.Text, maxLines = 1)
                Text(" /nt", style = MaterialTheme.typography.bodySmall, color = PinedrawColors.TextSecondary)
            }
            MetaLine(campsite)
            RatingLabel(campsite)
        }
    }
}

// Find campsites using their name or location
/**
 * Filters campsites based on the user's search.
 *
 * Searches campsite names and locations without considering uppercase
 * or lowercase letters.
 *
 * @param campsites List of campsites to search through.
 * @param searchText Text entered by the user.
 * @return A list of campsites matching the search.
 */
fun filterCampsites(
    campsites: List<Campsite>,
    searchText: String
): List<Campsite> {
    val keyword = searchText.trim()
    // Return campsites that match the search
    return campsites.filter { campsite ->
        campsite.name.contains(keyword, ignoreCase = true) || campsite.location.contains(keyword, ignoreCase = true)
    }
}
// For testing
/**
 * Gets the campsites that should appear on the Discover screen.
 *
 * Removes unpublished campsites and applies the user's search.
 *
 * @param campsites List of available campsites.
 * @param searchText Current search text.
 * @return A list of published campsites matching the search.
 */
fun getDisplayedCampsites(
    campsites: List<Campsite>,
    searchText: String
): List<Campsite> {
    val publishedCampsites = campsites.filter { it.published }
    return filterCampsites(publishedCampsites, searchText)
}
/**
 * Converts a campsite rating into a string of stars.
 *
 * Uses filled and empty stars to display the rating.
 * Decimal ratings are rounded down for the star display.
 *
 * @param rating The campsite's average rating.
 * @return A string containing five stars.
 */
fun getRatingStars(rating: Double): String {
    val fullStars = rating.toInt().coerceIn(0, 5)
    return "\u2605".repeat(fullStars) + "\u2606".repeat(5 - fullStars)
}
/**
 * Gets the text used to display a campsite's rating.
 *
 * Shows the numerical rating when available, or a message
 * when the campsite has no ratings.
 *
 * @param rating The average rating, or null if there are no ratings.
 * @return The formatted rating or "No ratings yet".
 */
fun getRatingText(rating: Double?): String {
    if (rating == null) {
        return "No ratings yet"
    }

    // Averages rarely divide evenly, so show one decimal place.
    return String.format(Locale.CANADA, "%.1f / 5", rating)
}

