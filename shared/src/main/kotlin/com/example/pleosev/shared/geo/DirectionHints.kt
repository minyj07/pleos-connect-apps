package com.example.pleosev.shared.geo

import com.example.pleosev.shared.api.GeoPoint

/**
 * Highway service areas have one station per carriageway, named after the city the carriageway leads
 * to: "괴산휴게소(창원방향)", "문경휴게소 (창원) 전기차충전소", "괴산휴게소(양평방향)". Straight-line route
 * geometry cannot tell the two sides apart, so the direction name is resolved to an approximate city
 * location and compared with the direction of travel.
 */
object DirectionHints {

    private val DIRECTION = Regex("""([가-힣]{2,4})\s*방향""")
    private val BRACKETED = Regex("""\(\s*([가-힣]{2,4})\s*\)""")

    /** Approximate city centres used as direction targets (expressway signage names). */
    private val CITIES: Map<String, GeoPoint> = mapOf(
        "서울" to GeoPoint(37.5665, 126.9780), "인천" to GeoPoint(37.4563, 126.7052), "수원" to GeoPoint(37.2636, 127.0286),
        "성남" to GeoPoint(37.4200, 127.1267), "하남" to GeoPoint(37.5393, 127.2149), "구리" to GeoPoint(37.5943, 127.1296),
        "의정부" to GeoPoint(37.7381, 127.0337), "용인" to GeoPoint(37.2411, 127.1776), "평택" to GeoPoint(36.9921, 127.1129),
        "안성" to GeoPoint(37.0080, 127.2797), "이천" to GeoPoint(37.2720, 127.4350), "여주" to GeoPoint(37.2984, 127.6370),
        "양평" to GeoPoint(37.4917, 127.4876), "춘천" to GeoPoint(37.8813, 127.7298), "홍천" to GeoPoint(37.6970, 127.8888),
        "원주" to GeoPoint(37.3422, 127.9202), "횡성" to GeoPoint(37.4917, 127.9853), "평창" to GeoPoint(37.3705, 128.3903),
        "강릉" to GeoPoint(37.7519, 128.8761), "속초" to GeoPoint(38.2070, 128.5918), "양양" to GeoPoint(38.0754, 128.6190),
        "인제" to GeoPoint(38.0697, 128.1707), "동해" to GeoPoint(37.5247, 129.1143), "삼척" to GeoPoint(37.4499, 129.1652),
        "제천" to GeoPoint(37.1326, 128.1910), "단양" to GeoPoint(36.9845, 128.3655), "충주" to GeoPoint(36.9910, 127.9260),
        "청주" to GeoPoint(36.6424, 127.4890), "천안" to GeoPoint(36.8151, 127.1139), "아산" to GeoPoint(36.7898, 127.0018),
        "당진" to GeoPoint(36.8898, 126.6459), "서산" to GeoPoint(36.7848, 126.4503), "홍성" to GeoPoint(36.6012, 126.6608),
        "보령" to GeoPoint(36.3333, 126.6128), "서천" to GeoPoint(36.0803, 126.6919), "공주" to GeoPoint(36.4466, 127.1190),
        "세종" to GeoPoint(36.4800, 127.2890), "대전" to GeoPoint(36.3504, 127.3845), "논산" to GeoPoint(36.1872, 127.0987),
        "보은" to GeoPoint(36.4894, 127.7295), "옥천" to GeoPoint(36.3064, 127.5713), "영동" to GeoPoint(36.1750, 127.7764),
        "무주" to GeoPoint(36.0070, 127.6608), "군산" to GeoPoint(35.9676, 126.7366), "익산" to GeoPoint(35.9483, 126.9577),
        "전주" to GeoPoint(35.8242, 127.1480), "진안" to GeoPoint(35.7917, 127.4248), "장수" to GeoPoint(35.6474, 127.5212),
        "남원" to GeoPoint(35.4164, 127.3904), "고창" to GeoPoint(35.4358, 126.7019), "담양" to GeoPoint(35.3211, 126.9882),
        "광주" to GeoPoint(35.1595, 126.8526), "나주" to GeoPoint(35.0160, 126.7108), "무안" to GeoPoint(34.9904, 126.4817),
        "목포" to GeoPoint(34.8118, 126.3922), "해남" to GeoPoint(34.5733, 126.5992), "완도" to GeoPoint(34.3110, 126.7550),
        "순천" to GeoPoint(34.9507, 127.4872), "광양" to GeoPoint(34.9407, 127.6959), "여수" to GeoPoint(34.7604, 127.6622),
        "함양" to GeoPoint(35.5205, 127.7251), "거창" to GeoPoint(35.6866, 127.9095), "진주" to GeoPoint(35.1800, 128.1076),
        "통영" to GeoPoint(34.8544, 128.4331), "마산" to GeoPoint(35.2140, 128.5800), "창원" to GeoPoint(35.2279, 128.6811),
        "김해" to GeoPoint(35.2285, 128.8894), "부산" to GeoPoint(35.1796, 129.0756), "울산" to GeoPoint(35.5384, 129.3114),
        "밀양" to GeoPoint(35.5038, 128.7467), "경주" to GeoPoint(35.8562, 129.2247), "포항" to GeoPoint(36.0190, 129.3435),
        "영덕" to GeoPoint(36.4153, 129.3653), "울진" to GeoPoint(36.9930, 129.4004), "대구" to GeoPoint(35.8714, 128.6014),
        "영천" to GeoPoint(35.9733, 128.9386), "고령" to GeoPoint(35.7262, 128.2628), "성주" to GeoPoint(35.9192, 128.2830),
        "칠곡" to GeoPoint(35.9955, 128.4017), "구미" to GeoPoint(36.1195, 128.3446), "김천" to GeoPoint(36.1398, 128.1136),
        "상주" to GeoPoint(36.4109, 128.1590), "문경" to GeoPoint(36.5865, 128.1867), "의성" to GeoPoint(36.3527, 128.6970),
        "안동" to GeoPoint(36.5684, 128.7294), "청송" to GeoPoint(36.4359, 129.0570), "영주" to GeoPoint(36.8057, 128.6240),
        // Also signed in the real station names (2026-11-04 full sync), e.g. "E1 용인처인휴게소(포천방향)", "워터 음성휴게소 남이방향".
        "포천" to GeoPoint(37.8949, 127.2003), "화도" to GeoPoint(37.6526, 127.3089), "일산" to GeoPoint(37.6584, 126.7700),
        "문산" to GeoPoint(37.8551, 126.7882), "시흥" to GeoPoint(37.3800, 126.8029), "판교" to GeoPoint(37.3947, 127.1112),
        "남이" to GeoPoint(36.5530, 127.4160), "완주" to GeoPoint(35.9046, 127.1622), "기장" to GeoPoint(35.2445, 129.2222),
        "서부산" to GeoPoint(35.1527, 128.9837),
    )

    /** The city the station's carriageway leads to, or null when the name carries no known direction. */
    fun target(stationName: String): GeoPoint? {
        DIRECTION.findAll(stationName).forEach { m -> city(m.groupValues[1])?.let { return it } }
        BRACKETED.findAll(stationName).forEach { m -> city(m.groupValues[1])?.let { return it } }
        return null
    }

    /** Two signed cities can run together ("하행_음성제천방향", "서울인천방향"); either one gives the direction. */
    private fun city(word: String): GeoPoint? =
        CITIES[word] ?: if (word.length == 4) CITIES[word.take(2)] ?: CITIES[word.takeLast(2)] else null

    /**
     * True when [stationName] is signed for a direction more than [toleranceDeg] away from the direction
     * of travel at [at]: the station is on the other carriageway and unreachable without a U-turn.
     */
    fun isOppositeCarriageway(stationName: String, at: GeoPoint, travelBearingDeg: Double, toleranceDeg: Double = 100.0): Boolean {
        val target = target(stationName) ?: return false
        if (Geo.distanceKm(at, target) < NEAR_TARGET_KM) return false // at the city itself the sign says nothing
        return Geo.angleDiffDeg(Geo.bearingDeg(at, target), travelBearingDeg) > toleranceDeg
    }

    private const val NEAR_TARGET_KM = 5.0
}
