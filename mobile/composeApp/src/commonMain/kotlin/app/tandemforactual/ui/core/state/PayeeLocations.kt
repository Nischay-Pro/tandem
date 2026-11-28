package app.tandemforactual.ui.core.state

import app.tandemforactual.ui.core.platform.GeoPoint
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/** Places a payee was paid at, newest first, each rounded to about a hundred metres. */
internal typealias PayeeLocations = Map<String, List<GeoPoint>>

internal fun payeeLocationsKey(budgetId: String): String = "internal:payee-locations:$budgetId"

internal fun GeoPoint.coarse(): GeoPoint = GeoPoint(roundCoarse(latitude), roundCoarse(longitude))

private fun roundCoarse(value: Double): Double = (value * COARSE_SCALE).roundToLong() / COARSE_SCALE

/** Adds [point] for [payeeId] unless it's already there, keeping the newest few places. */
internal fun PayeeLocations.adding(
    payeeId: String,
    point: GeoPoint,
): PayeeLocations {
    val place = point.coarse()
    val kept = (listOf(place) + this[payeeId].orEmpty().filterNot { it == place }).take(MAX_PLACES_PER_PAYEE)
    return this + (payeeId to kept)
}

internal fun distanceMetres(
    a: GeoPoint,
    b: GeoPoint,
): Double {
    val lat1 = a.latitude.toRadians()
    val lat2 = b.latitude.toRadians()
    val dLat = lat2 - lat1
    val dLon = (b.longitude - a.longitude).toRadians()
    val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
    return 2 * EARTH_RADIUS_METRES * asin(sqrt(h))
}

private fun Double.toRadians(): Double = this * kotlin.math.PI / 180

/** Payees paid within [radiusMetres] of [here], closest first. */
internal fun PayeeLocations.nearby(
    here: GeoPoint,
    radiusMetres: Double = NEARBY_RADIUS_METRES,
): List<String> =
    mapNotNull { (payeeId, places) -> places.minOfOrNull { distanceMetres(here, it) }?.let { payeeId to it } }
        .filter { it.second <= radiusMetres }
        .sortedBy { it.second }
        .map { it.first }

/** `payee|lat,lon;lat,lon` per line. */
internal fun encodePayeeLocations(locations: PayeeLocations): String =
    locations.entries
        .filter { it.value.isNotEmpty() }
        .sortedBy { it.key }
        .joinToString("\n") { (payeeId, places) -> payeeId + "|" + places.joinToString(";") { "${it.latitude},${it.longitude}" } }

internal fun decodePayeeLocations(stored: String?): PayeeLocations =
    stored
        .orEmpty()
        .lines()
        .mapNotNull { line ->
            val payeeId = line.substringBefore('|').takeIf { it.isNotBlank() && '|' in line } ?: return@mapNotNull null
            val places =
                line.substringAfter('|').split(';').mapNotNull { pair ->
                    val (lat, lon) = pair.split(',').takeIf { it.size == 2 } ?: return@mapNotNull null
                    GeoPoint(lat.toDoubleOrNull() ?: return@mapNotNull null, lon.toDoubleOrNull() ?: return@mapNotNull null)
                }
            places.takeIf { it.isNotEmpty() }?.let { payeeId to it }
        }.toMap()

private const val COARSE_SCALE = 1_000.0
private const val MAX_PLACES_PER_PAYEE = 5
private const val NEARBY_RADIUS_METRES = 300.0
private const val EARTH_RADIUS_METRES = 6_371_000.0
