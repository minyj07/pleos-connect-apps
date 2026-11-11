package com.example.pleosev.advisor.background

import com.example.pleosev.advisor.AdvisorCandidate
import com.example.pleosev.advisor.AdvisorState
import com.example.pleosev.advisor.domain.AlertTopic
import com.example.pleosev.advisor.domain.ChargeNeed
import com.example.pleosev.advisor.domain.NaviAction
import com.example.pleosev.advisor.domain.NaviActionKind
import com.example.pleosev.advisor.domain.NeedKind
import com.example.pleosev.advisor.domain.PlannedStopVerdict
import com.example.pleosev.advisor.domain.Severity
import com.example.pleosev.advisor.domain.StopRole
import com.example.pleosev.advisor.domain.candidate
import com.example.pleosev.advisor.domain.station
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AlertTextsTest {

    private fun need(kind: NeedKind, severity: Severity) = ChargeNeed.NONE.copy(kind = kind, severity = severity)

    private fun card(name: String, statId: String, available: Int) = AdvisorCandidate(
        candidate(station(name = name, available = available, statId = statId)),
        actions = listOf(NaviAction.SearchInNavi("$name 전기차충전소")),
        refinedByNavi = true,
    )

    @Test
    fun aBusyPlannedStopSuggestsAnAlternativeThatIsFreeNow() {
        val state = AdvisorState(
            need = need(NeedKind.PLANNED_STOP, Severity.ADVISORY),
            attention = Severity.RECOMMENDED,
            plannedVerdict = PlannedStopVerdict.BUSY,
            candidates = listOf(card("충주휴게소", "A", available = 0), card("문경휴게소(창원방향)", "B", available = 4)),
        )
        val alert = assertNotNull(AlertTexts.alert(state))
        assertEquals(AlertTopic.PLANNED_STOP, alert.topic)
        assertEquals("경로의 충전소에 빈 충전기가 없어요", alert.title)
        assertEquals("대안 문경휴게소(창원방향) · 급속 4/5 · +4분 · 도착 18%", alert.text)
        assertEquals("B", alert.station?.statId)
        assertEquals(NaviActionKind.SEARCH_IN_NAVI, alert.action)
    }

    @Test
    fun noAlertForAFreePlannedStopOrAnAdvisoryNeed() {
        val planned = need(NeedKind.PLANNED_STOP, Severity.ADVISORY)
        assertNull(AlertTexts.alert(AdvisorState(need = planned, attention = Severity.ADVISORY, plannedVerdict = PlannedStopVerdict.OK)))
        // An unclear planned stop raises the in-app banner but pops up only when nearly empty.
        assertNull(AlertTexts.alert(AdvisorState(need = planned, attention = Severity.RECOMMENDED, plannedVerdict = PlannedStopVerdict.UNKNOWN)))
        assertNull(AlertTexts.alert(AdvisorState(need = need(NeedKind.ON_ROUTE, Severity.ADVISORY), attention = Severity.ADVISORY)))
    }

    @Test
    fun aFullChargerDestinationAndAnUnreachableStopSayWhichStopTheyMean() {
        val destination = AdvisorState(
            need = need(NeedKind.PLANNED_STOP, Severity.ADVISORY),
            attention = Severity.RECOMMENDED,
            plannedStopRole = StopRole.DESTINATION,
            plannedVerdict = PlannedStopVerdict.BUSY,
            candidates = listOf(card("판교역 공영주차장", "MEFK0001", available = 2)),
        )
        assertEquals("목적지 충전소에 빈 충전기가 없어요", AlertTexts.alert(destination)?.title)

        val unreachable = AdvisorState(
            need = need(NeedKind.PLANNED_STOP, Severity.URGENT),
            attention = Severity.URGENT,
            plannedStopRole = StopRole.NAVI,
            plannedVerdict = PlannedStopVerdict.UNREACHABLE,
            candidates = listOf(card("E-pit 판교", "HDFK0003", available = 2)),
        )
        val alert = assertNotNull(AlertTexts.alert(unreachable))
        assertEquals("긴급 · 경로의 충전소까지 배터리가 부족해요", alert.title)
        assertEquals("가까운 E-pit 판교 · 급속 2/3 · +4분 · 도착 18%", alert.text)
    }

    @Test
    fun urgentWithoutCardsOffersTheNavisChargerList() {
        val state = AdvisorState(
            need = need(NeedKind.NEARBY, Severity.URGENT),
            attention = Severity.URGENT,
            problem = "충전 정보 서버에 연결할 수 없어요",
        )
        val alert = assertNotNull(AlertTexts.alert(state))
        assertEquals("긴급 · 배터리가 얼마 남지 않았어요", alert.title)
        assertEquals("충전 정보 서버에 연결할 수 없어요", alert.text)
        assertNull(alert.station)
        assertNull(alert.action)
    }
}
