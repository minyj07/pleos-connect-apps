package com.example.pleosev.server.source

/** The two KECO EvCharger operations the server needs. */
interface ChargerSource {
    val name: String

    /** getChargerInfo: stations and chargers (with their current `stat`) of one 시도 (zcode). */
    suspend fun chargerInfo(zcode: String, pageNo: Int, numOfRows: Int): KecoPage

    /** getChargerStatus: chargers whose status changed in the last [periodMin] minutes (1..10). */
    suspend fun chargerStatus(periodMin: Int, pageNo: Int, numOfRows: Int, zcode: String? = null): KecoPage
}
