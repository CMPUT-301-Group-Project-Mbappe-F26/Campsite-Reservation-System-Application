package com.example.campsitereservationapp.admin

import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.model.User

/** Where an uploaded image is used. */
enum class AdminImageSource(val label: String) {
    CAMPSITE("Campsite photo"),
    PROFILE("Profile photo")
}

/**
 * One uploaded image shown to admins in the image browser (US 03.06.01).
 *
 * @property id Stable key: the owning document's ID plus the photo's position.
 * @property url Download URL of the image.
 * @property source Whether it is a campsite promo photo or a profile photo.
 * @property ownerId ID of the campsite or user the image belongs to.
 * @property ownerName Name of the campsite or user the image belongs to.
 * @property position Index of the photo within its campsite; 0 is the cover. Always 0 for profiles.
 */
data class AdminImage(
    val id: String,
    val url: String,
    val source: AdminImageSource,
    val ownerId: String,
    val ownerName: String,
    val position: Int = 0
)

/**
 * Collects every uploaded image: each campsite's promo photos, then each profile photo.
 *
 * Blank URLs are skipped, and campsites and profiles without a name get a readable fallback.
 *
 * @param campsites All campsites, published or not.
 * @param users All registered profiles.
 * @return Campsite photos sorted by campsite name, followed by profile photos sorted by user name.
 */
fun adminImages(campsites: List<Campsite>, users: List<User>): List<AdminImage> {
    val campsitePhotos = campsites
        .sortedBy { it.name.lowercase() }
        .flatMap { campsite ->
            campsite.imageUrls.mapIndexedNotNull { index, url ->
                url.takeIf { it.isNotBlank() }?.let {
                    AdminImage(
                        id = "campsite-${campsite.id}-$index",
                        url = it,
                        source = AdminImageSource.CAMPSITE,
                        ownerId = campsite.id,
                        ownerName = campsite.name.ifBlank { "Unnamed campsite" },
                        position = index
                    )
                }
            }
        }
    val profilePhotos = users
        .filter { it.profileImageUrl.isNotBlank() }
        .sortedBy { it.name.lowercase() }
        .map { user ->
            AdminImage(
                id = "profile-${user.id}",
                url = user.profileImageUrl,
                source = AdminImageSource.PROFILE,
                ownerId = user.id,
                ownerName = user.name.ifBlank { "Unnamed profile" }
            )
        }
    return campsitePhotos + profilePhotos
}
