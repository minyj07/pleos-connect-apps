package com.example.pleosev.shared.charger

import com.example.pleosev.shared.api.Connector

/**
 * KECO (한국환경공단) `chgerType` codes. 01~07 follow the 활용가이드; 08~10 appeared later and their
 * meaning (DC콤보 완속 / NACS / DC콤보+NACS) still has to be confirmed against real data (spike 0-4).
 */
object ChargerTypes {

    private val DC_CONNECTORS = setOf(Connector.DC_COMBO, Connector.DC_CHADEMO, Connector.NACS)

    /** Plugs offered by a charger type; unknown codes map to an empty set and are ignored. */
    fun connectors(typeCode: String): Set<Connector> = when (typeCode.trim().padStart(2, '0')) {
        "01" -> setOf(Connector.DC_CHADEMO)
        "02" -> setOf(Connector.AC_SLOW)
        "03" -> setOf(Connector.DC_CHADEMO, Connector.AC3)
        "04" -> setOf(Connector.DC_COMBO)
        "05" -> setOf(Connector.DC_CHADEMO, Connector.DC_COMBO)
        "06" -> setOf(Connector.DC_CHADEMO, Connector.AC3, Connector.DC_COMBO)
        "07" -> setOf(Connector.AC3)
        "08" -> setOf(Connector.DC_COMBO) // DC콤보(완속): low power, filtered out by minKw
        "09" -> setOf(Connector.NACS)
        "10" -> setOf(Connector.DC_COMBO, Connector.NACS)
        else -> emptySet()
    }

    /** True when the charger offers any DC plug. The server indexes only these (AC chargers are dropped). */
    fun isDc(connectors: Set<Connector>): Boolean = connectors.any { it in DC_CONNECTORS }

    /** Output to assume when KECO leaves `output` empty: classic DC chargers are 50 kW, AC 7 kW. */
    fun defaultOutputKw(connectors: Set<Connector>): Int = if (isDc(connectors)) 50 else 7
}
