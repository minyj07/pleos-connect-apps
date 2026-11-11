package com.example.pleosev.shared.geo

import com.example.pleosev.shared.api.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeoTest {
    private val seoulCityHall = GeoPoint(37.5663, 126.9779)
    private val busanStation = GeoPoint(35.1151, 129.0422)

    @Test
    fun seoulToBusanIsAbout329Km() {
        val km = Geo.distanceKm(seoulCityHall, busanStation)
        assertTrue(km in 325.0..334.0, "got $km km")
    }

    @Test
    fun distanceIsSymmetricAndZeroForSamePoint() {
        assertEquals(Geo.distanceKm(seoulCityHall, busanStation), Geo.distanceKm(busanStation, seoulCityHall), 1e-9)
        assertEquals(0.0, Geo.distanceKm(seoulCityHall, seoulCityHall), 1e-9)
    }

    @Test
    fun busanLiesSouthEastOfSeoul() {
        val bearing = Geo.bearingDeg(seoulCityHall, busanStation)
        assertTrue(bearing in 140.0..150.0, "got $bearing°")
        assertEquals(0.0, Geo.bearingDeg(GeoPoint(36.0, 127.0), GeoPoint(37.0, 127.0)), 1e-6)
    }

    @Test
    fun angleDifferenceWrapsAround() {
        assertEquals(20.0, Geo.angleDiffDeg(350.0, 10.0), 1e-9)
        assertEquals(180.0, Geo.angleDiffDeg(10.0, 190.0), 1e-9)
        assertEquals(0.0, Geo.angleDiffDeg(90.0, 450.0), 1e-9)
    }

    @Test
    fun detourIsNearZeroOnTheLineAndGrowsOffIt() {
        val a = GeoPoint(36.0, 127.0)
        val b = GeoPoint(36.0, 128.0)
        assertTrue(Geo.detourKm(a, GeoPoint(36.0, 127.5), b) < 0.1)
        val off = Geo.detourKm(a, GeoPoint(36.5, 127.5), b)
        assertTrue(off in 50.0..56.0, "got $off km")
    }
}
