# Phase 0 스파이크 결과 — 순정 내비(NaviHelper)·알림·백그라운드 실측

> 2026-11-02, Pleos Connect Emulator v2.1.0 (AVD `Pleos_Connect_v2_1`, 사용자 10), NaviHelper 3.0.5.
> 내비 앱 `ai.umos.maps.android.navigation.app`, Navi Service `ai.pleos.playground.service.navi`(priv-app, versionCode 55).
> 프로브: `app/src/debug/java/com/example/pleosev/debug/` (`NaviProbe`, `NaviProbeActivity`, `ProbeNotifications`, `ProbeActionReceiver`, `ProbeForegroundService`) — debug 빌드에만 들어갑니다.
> data.go.kr 실측(0-4)은 인증키가 생기면 `scripts/keco-probe.ps1` 로 진행합니다.

## 1. 결론 요약

| 질문 | 실측 결과 |
| --- | --- |
| 에뮬레이터에서 NaviHelper가 동작하나 | **예.** `NAVI_START` 액션의 `NaviService` 와 내비 앱이 탑재돼 있음. `initialize` 50~280 ms(main), 콜백은 binder 스레드 |
| `poiId=""`(좌표만)로 경로·경유지 요청 | **받아들임.** 경로 탐색 응답 약 8~12 s. 경유지 추가·삭제는 내비가 기존 경로를 취소(`onRouteCancelled`)한 뒤 다시 탐색 |
| 도착 충전값 단위 | **Wh(남은 배터리 에너지).** `RouteStartInfo`/`DrivingState.remainDestinationChargingValue`, `RouteStateInfo.remainDestinationChargingInt`/`remainWaypointChargingInt` 모두 Wh. `TripInfo.estimatedSocAtDest` 는 %, `energyConsumption` 은 Wh. 두 경로 모두 약 6.85 km/kWh로 계산됨 |
| 내비 최소 도착 SOC | `DrivingState.targetSoc = 10` (%) |
| 내비가 충전소를 자동으로 넣나 | **넣음.** SOC 42 %(32.5 kWh)로 부산역 경로를 요청하자 "워터 괴산휴게소 창원방향 전기차충전소"(350 kW, 8기, 123 km 지점)를 경유지로 자동 추가. `DrivingInfo.waypoints` 에 `isChargingStation=true` 로만 나오고 `onWayPointInfo` 에는 없음. `isRecommendedChargingStation` 은 false 로 옴 |
| 자동 충전 경유지를 지울 수 있나 | **없음.** `removeWaypoint(SECOND/ALL)` 은 우리가 넣은 경유지에만 작동하고, 내비는 자동 충전 경유지를 곧바로 다시 계획 |
| 자동 충전 경유지의 좌표·POI id | `requestTrip(목적지)` → `TripInfo.summary.routePoints` 에 `settingType=AUTO, poiType=EV_CHARGING` 항목으로 좌표 + 내비 `poiId`(예: `36mc456q4pqscm5nqhe4`), `ratioOnRoute`(%) |
| 충전소를 좌표만으로 경유지에 넣으면 | 일반 경유지로 취급(`isChargingStation=false`) → 내비가 **자기 충전 경유지를 따로 또 추가**(예: 문경휴게소 200 kW). 사용자에게 중복 정차가 생김 |
| 내비 `poiId` 와 함께 넣으면 | **충전소로 인식**(`isChargingStation=true`, kW·대수 채워짐). 이름이 내비 POI 이름으로 바뀌고 자동 충전 경유지를 추가하지 않음 |
| 환경공단 statId → 내비 poiId 변환 | NaviHelper로는 **불가.** `requestTrip` 은 좌표를 도로에 맞춰 보정할 뿐 `poiId` 는 비어 있음. `poiProvider` = ME / KECO / EV + `poiId` = statId 로 넣어도 충전소로 인식하지 않음 |
| `WaypointInfo` 좌표 | **0.0 으로 옴.** 이름·주소(역지오코딩)·남은 거리/시간·`poiId` 만 유효 |
| `requestTrip` | 약 0.2~1.2 s, 현재 안내 경로에 영향 없음. 현재 위치 → 지정 지점을 새로 계산(현재 경로의 경유지는 반영 안 함) |
| 스스로 오는 콜백(정차 중) | `onCurrentRoadSpeedLimit` 만 약 1 s 간격. 나머지는 경로 변경 시점이나 `get*Info` 호출 때만 |
| `requestReRoute` | 정차 중 18 s 안에 응답 없음 |
| 에뮬레이터에서 위치 이동 | `adb emu geo fix` 는 Android GPS 만 바꾸고 **내비 위치는 그대로**(성남 시흥동 기본값). 주행 중 동작은 내비 모의주행이나 실차에서 확인해야 함 |
| TBT 목록 | 경로 전체의 IC·휴게소·졸음쉼터 이름과 구간 거리를 순서대로 제공 → 경로 위 휴게소를 식별할 수 있음 |
| 충전 운영사 목록 | `getChargerOperatorInfo` 의 id 가 환경공단 busiId 체계와 같음(HD=E-pit, ME=환경부, KP=한국전력, ST=SK일렉링크 …) |
| 내비 내부 충전소 데이터 | 내비 로그상 충전기 id 가 환경공단 형식(`operatorId=ME, stationId=MEMOOEV1, chargerId=MEMOOEV1_08`). 내비 POI 하나에 환경부 statId 가 붙어 있는 구조 |
| 순정 내비의 현황 표시 | "주변 충전소" 화면이 급속/완속 사용 가능 대수와 "신뢰도"를 이미 보여 줌(경로 주변 필터 포함) |
| 화면 배치 | 왼쪽 Driving view 고정, 오른쪽 앱 영역을 **내비나 우리 앱 중 하나가 통째로** 사용. 운전 중 우리 앱 화면은 보통 가려져 있음 |
| 헤드업 알림 | 내비 위에 카드 + 액션 버튼으로 표시. 주차, 기어 D(UX 제한 80 = NO_VIDEO+NO_SETUP)에서 모두 표시(카테고리 recommendation·navigation 동일). 기어 D에서 1회 미표시(원인 미상). **2026-11-03 에는 같은 프로브 알림도 표시되지 않음(§7)** |
| 알림 액션 → 경유지 추가 | non-exported `BroadcastReceiver` + `FLAG_IMMUTABLE` 로 **앱을 띄우지 않고** `addWaypoint` 성공(내비 재탐색 약 10 s) |
| `POST_NOTIFICATIONS` 부여 | AAOS 는 사용자 10 에서 실행 → `adb shell pm grant --user current …` 필요(기본 사용자 0 에 주면 적용 안 됨) |
| 백그라운드 | 앱이 뒤로 가면 1~2분 안에 cached → **frozen**(`cgroup.freeze=1`). 동결 중 콜백은 버퍼에 쌓였다가 깨어날 때 한꺼번에 도착 |
| `specialUse` FGS | 포그라운드에서 시작 가능. 내비가 앞에 있어도 `fg-service` 상태 유지, 90 s 동안 콜백 1 s 간격 지속. **Pleos 정책상 허용 여부는 미확인** |
| `requestNavigationScreen` | `NearbyChargingStations` → 내비가 앞으로 나와 충전소 패널 표시. `SearchPlace` → 내비가 검색은 하지만 앞으로 나오지 않음. `Foreground` → 반응 없음 |
| 주행 규제 토글 | 에뮬레이터에 `cmd hmg_car_service` 없음. Virtual Console 기어 D 로 Driving State 1(IDLING) + UX 제한 80. 웹 UI 에는 속도 카드가 없지만 `PUT /vehicle/controls?speed=<정수 km/h>`(`scripts/pleos-vc.ps1 -SpeedKph`)로 차속을 넣으면 MOVING 상태가 됨(2026-11-03 드라이브 코치 세션에서 확인) |

스크린샷: `docs/screenshots/spike-navi-auto-charging-stop.png`(자동 충전 경유지·도착 18 %), `spike-navi-nearby-chargers.png`(순정 내비 충전소 현황), `spike-hun-over-navi.png`(내비 위 헤드업 알림), `spike-hun-driving-uxr80.png`(기어 D 상태 헤드업 알림).

## 2. 주요 로그 발췌

```
# SOC 100 %(77.4 kWh), 부산역 경로
onRouteStarted   RouteStartInfo(duration=14640, distance=378000, tollFare=14510, name=부산역, remainDestinationChargingValue=22102)
onDrivingInfo    destination=DrivingState(name=부산역, duration=14640, distance=378000, remainDestinationChargingValue=22102, targetSoc=10, …), waypoints=[]
onTripInfo       TripInfo(distance=378458, eta=14688, estimatedSocAtDest=9, energyConsumption=55297.1, …)   # 77.4 kWh − 55.3 kWh ≈ 22.1 kWh

# SOC 42 %(32.5 kWh), 같은 경로 → 내비가 충전 경유지 자동 추가
onRouteStarted   RouteStartInfo(duration=5040, distance=123000, name=워터 괴산휴게소 창원방향 전기차충전소, remainDestinationChargingValue=14601)
onRouteStateInfo remainDestinationChargingInt=7740, remainWaypointChargingInt=[14601, 0]
onDrivingInfo    waypoints=[DrivingState(name=워터 괴산휴게소 창원방향 전기차충전소, distance=123000, remainDestinationChargingValue=14601,
                            isChargingStation=true, isRecommendedChargingStation=false, maxChargingPower=350, targetSoc=10, chargerCount=8, chargingPlan=null)]
onWayPointInfo   []                                                          # 자동 충전 경유지는 여기 없음
onTripInfo       routePoints=[RoutePointInfo(latitude=36.8327504, longitude=127.9583201, poiName=워터 괴산휴게소 창원방향 전기차충전소,
                              poiId=36mc456q4pqscm5nqhe4, ratioOnRoute=32.54, poiType=EV_CHARGING, pointType=WAYPOINT, settingType=AUTO), …]

# 같은 지점을 좌표만으로 경유지 추가 → 내비가 문경휴게소를 따로 또 추가
onDrivingInfo    waypoints=[DrivingState(name=Goesan-SA-coords-only, isChargingStation=false, …),
                            DrivingState(name=문경휴게소 (창원) 전기차충전소, distance=155000, isChargingStation=true, maxChargingPower=200, chargerCount=5)]
onWayPointInfo   [WaypointInfo(name=Goesan-SA-coords-only, address=괴산군 장연면, latitude=0.0, longitude=0.0, remainDistance=123000, poiId=)]

# 내비 poiId 와 함께 추가 → 충전소로 인식, 중복 없음
onDrivingInfo    waypoints=[DrivingState(name=워터 괴산휴게소 창원방향 전기차충전소, isChargingStation=true, maxChargingPower=350, chargerCount=8)]
onWayPointInfo   [WaypointInfo(name=워터 괴산휴게소 창원방향 전기차충전소, …, latitude=0.0, longitude=0.0, poiId=36mc456q4pqscm5nqhe4)]
```

## 3. 설계에 주는 영향 (Phase 1~3 수정 사항)

1. **차별점 재정의.** 순정 내비가 이미 충전소 현황과 자동 충전 경유지를 제공한다. 앱의 가치는 ① 충전이 필요할 때 먼저 알리기, ② **내비가 고른 충전소를 실시간으로 점검하고 만차·고장·상태 불명이면 대안 제시**, ③ 운영사·출력·우회 선호 반영으로 좁힌다. 계획의 케이스 B가 주 시나리오가 된다.
2. **내비 계획 충전소 식별.** `onDrivingInfo` 로 충전 경유지 존재를 알고, `requestTrip(목적지)` 의 `routePoints`(AUTO, EV_CHARGING)로 좌표와 `poiId` 를 얻어 서버 `/stations/match`(좌표 100 m 이내 + 이름)로 statId 를 찾는다. `DrivingState`·`WaypointInfo` 에는 좌표가 없으므로 이 경로가 유일하다.
3. **대안 적용 방식.** 좌표만으로 넣으면 중복 정차가 생기므로 기본 액션을 바꾼다.
   - 대안이 내비가 아는 충전소(`poiId` 확보)면 `addWaypoint(poiId)` 로 바로 적용.
   - 그 외에는 "내비에서 이 충전소 찾기"(`SearchPlace(이름)` 후 내비 전환)를 1차 액션으로 두고, 좌표 추가는 "내비가 다른 충전소를 한 번 더 넣을 수 있음" 안내와 함께 보조 액션으로 둔다.
   - Pleos 기술지원에 외부 충전소 ID(`poiProvider`) 지원 또는 POI 조회 API를 문의한다.
4. **주 접점은 헤드업 알림.** 운전 중 앱 영역은 보통 내비가 차지한다. 알림 카드 + 액션 1개를 기본 UX로 하고, 앱 화면은 상세·설정용으로 둔다.
5. **백그라운드는 `specialUse` FGS 전제.** FGS 없이는 1~2분 안에 동결된다. 앱이 보일 때 시작하고 경로 종료 시 멈춘다. Pleos 정책 확인 전까지는 "앱이 보일 때만 감시"를 대안으로 유지한다.
6. **도착 SOC 계산.** 내비 값(Wh) ÷ 배터리 용량(`INFO_EV_BATTERY_CAPACITY`) → %. reserve 는 `targetSoc`(10 %)와 사용자 설정 중 큰 값.
7. **경로 기하.** TBT 목록의 휴게소(누적 거리)를 경로 위 후보로 우선 사용하고, 직선 타원은 보조로 둔다. `requestTrip`(0.2 s)은 상위 후보 정밀화에 충분히 빠르다.
8. **운영사 id 는 busiId 그대로** 선호/회피 설정에 쓴다.
9. **테스트 한계.** 에뮬레이터에서 내비 위치를 움직일 수 없으므로 주행 중 시나리오(주기 콜백, 경유지 도착)는 Fake 내비로 개발하고 내비 모의주행이나 실차로 확인한다.

## 4. 남은 확인

- **0-4 data.go.kr 실측**: 2026-11-04 완료, 결과는 §8. 다시 재려면 `deploy/advisor.env`에 키를 넣은 뒤 `pwsh -File scripts/keco-probe.ps1` (약 15회 호출, fixture 저장).
- **0-5 Pleos 기술지원 문의**: 아직 답이 없는 것은 비미디어 `specialUse` FGS 허용, 주행 중(MOVING) 헤드업 알림 규칙, 서드파티 앱 헤드업이 표시되는 조건(§7: 2026-11-03 에뮬레이터에서 미표시), 외부 충전소 ID(`poiProvider`)·POI 조회 API입니다.
  - 현대자동차그룹 차량용 앱 개발 공모전 통합 온라인 설명회(2026-10-28, [다시보기](https://www.youtube.com/watch?v=na6tSJ44pWc))에서 확인한 것:
    - 실차 시연은 지원하지 않고, 앱 검증은 Pleos Playground 에뮬레이터로 한다(21:04).
    - 외부 API와 퍼블릭 네트워크를 쓸 수 있다(37:49). 중계 서버 구조는 그대로 써도 된다.
    - 개발 중에는 권한 승인 없이 매니페스트 선언만으로 공개 SDK(Vehicle·NaviHelper·STT/TTS·FusedLocation)를 쓴다. 승인은 App Market 배포 심사에서 보며, 승인 안 된 권한을 선언하면 심사를 통과하지 못한다(32:29, 38:17).
    - 공모전에는 아직 공개되지 않은 SDK도 제공할 예정이다(25:56).
  - 남은 질문은 공모전 문의 메일(appdevchallenge@hyundai-partners.com)로 보낼 수 있다. 개발 질문도 받아 현대자동차그룹·42dot에 전달하고, 질문이 많으면 공식 홈페이지 FAQ에 올린다고 했다(39:27~40:13).
- 에뮬레이터 미확인: MOVING 상태 헤드업(차속 API 로 재현 가능), `requestReRoute` 동작, 내비 모의주행으로 주행 중 콜백 주기.

## 5. 프로브 사용법

```powershell
$env:ANDROID_SERIAL = "emulator-5556"     # Pleos AVD (폰 AVD 와 함께 떠 있을 때)
adb shell am start -n com.example.pleosev/.debug.NaviProbeActivity --es probe init,getall
adb shell am start -n com.example.pleosev/.debug.NaviProbeActivity --es probe route_busan
adb shell am start -n com.example.pleosev/.debug.NaviProbeActivity --es custom wp --es name Test --es lat 36.83 --es lon 127.95 [--es poiId X] [--es index SECOND]
adb shell am start -n com.example.pleosev/.debug.NaviProbeActivity --es probe notify_later   # 8 s 뒤 알림(그 사이 내비를 앞으로)
adb shell pm grant --user current com.example.pleosev android.permission.POST_NOTIFICATIONS
adb logcat -s NaviProbe
```
동작 목록은 `NaviProbe.ACTIONS`. 내비를 앞으로 가져올 때는 `adb shell am start --user current -n ai.umos.maps.android.navigation.app/.MainActivity`.

## 6. Phase 2 E2E 에서 추가로 확인한 것 (2026-11-03)

앱 통합 E2E 중 새로 알게 된 동작입니다. 앱 쪽 대응은 [ADVISOR_APP.md](ADVISOR_APP.md).

| 항목 | 결과 |
| --- | --- |
| `requestTrip(목적지)` 와 수동 경유지 | 현재 경로의 수동 경유지를 **무시**하고 새로 계획함. 문경 충전소를 경유지로 넣은 경로에서도 `routePoints` 는 괴산 AUTO 충전소 + 목적지 |
| 내비 검색 → POI 화면 "경유지로" | 충전기 현황(예: 350 kW DC콤보 3대 중 2대 사용 가능)과 "목적지로 / 경유지로" 버튼이 있음. "경유지로" 로 넣은 충전소는 충전소로 인식되고, 내비가 넣었던 자동 충전 경유지(괴산)는 빠짐 |
| 그 경유지의 좌표 | `WaypointInfo` 는 여전히 0.0. 대신 `getRecentDestinationInfo` 목록 맨 앞에 같은 `poiId` 로 좌표와 함께 남음(예: 36.6215556, 128.1495167) |
| 경유지 변경 후 `DrivingInfo` | 경유지 삭제 직후 0 으로 채운 `DrivingInfo`(초기화)가 오고, 약 10 s 뒤 새 경로의 정차지(내비 자동 충전소 포함)가 옴. 앱이 살아 있으면 놓치지 않음 |
| `routeId` | `CurrentLocationInfo.routeId` 는 시간 기반(v1) UUID 형식. 재탐색마다 바뀌는지는 아직 확인하지 않음(앱은 경로 키로 쓰고, 충전 경유지 이름도 함께 비교) |
| `requestRoute` | 내비가 스스로 앞으로 나와 안내를 시작함(`SearchPlace` 와 다름) |
| 내비의 SOC | 배터리 잔량(kWh) ÷ 용량으로 계산하는 것으로 보임. Virtual Console 에서 SOC 만 15 % 로 바꾸고 잔량을 32.5 kWh 로 두면 2 km 앞 충전소 도착 SOC 를 41 % 로 답함 → 테스트 때 `-Soc` 와 `-BatteryKwh` 를 함께 바꿀 것 |
| SOC 가 `targetSoc`(10 %) 미만일 때 | 2 km 앞 충전소로 가는 `requestTrip` 에도 AUTO 충전 경유지가 붙음(내비가 최소 도착 SOC 를 지키려 함) |

스크린샷: `docs/screenshots/e2e-navi-search-detail.png`(내비 POI 화면), `e2e-advisor-*.png`.

## 7. Phase 3 E2E 에서 추가로 확인한 것 (2026-11-03)

백그라운드 감시·알림 E2E 중 관찰한 플랫폼 동작입니다. 앱 쪽 대응은 [ADVISOR_APP.md](ADVISOR_APP.md) "백그라운드 감시와 알림".

| 항목 | 결과 |
| --- | --- |
| `specialUse` FGS(실제 `AdvisorService`) | 내비를 앞에 두고 150 s 뒤에도 `curProcState=4`(FOREGROUND_SERVICE), `isFrozen=false`. 서비스를 멈추면 cached(15) → 150 s 안에 `isFrozen=true` |
| `routeId` | 안내 시작 약 5분 반 뒤 사용자 조작 없이 바뀜(내비 자체 재탐색). 경유지 변경 때도 바뀜. 같은 여행을 구분하는 키로 쓸 수 없음 |
| 헤드업 표시 | IMPORTANCE_HIGH(카테고리 recommendation, 버튼 3개) 알림을 Pleos 헤드업 서비스 `ai.umos.notification`(`HeadUpNotificationListenerService`)이 받지만(`isSystemImportantNotification(privileged=false, platformSigned=false, importance=4`) 카드를 그리지 않음. Phase 0 프로브 알림(버튼 1개)도 같음, 내비가 앞에 있어도·재부팅해도 같음 |
| 같은 때 시스템 UI | `NotificationCenterListener: notificationChannel is null` → `UNKNOWN NotificationPosted`, `NotificationHistory: Attempted to add notif for locked/gone/disabled user 10`(사용자 10 은 RUNNING_UNLOCKED). 알림 센터에는 전날 21:25 프로브 알림만 남아 있었고, 재부팅 뒤에는 종 아이콘도 사라짐 |
| `heads_up_notifications_enabled` | 전역 값이 0. 1 로 바꿔도 차이가 없어 0 으로 되돌림(같은 에뮬레이터의 드라이브 코치 세션이 바꾼 값도 아님) |
| 알림이 연 투명 액티비티 | Pleos 앱 영역(`HOME_SCREEN TaskViewManager`)에 별도 태스크로 떴다 0.13 s 만에 사라짐. `distractionOptimized=true` 필요 |
| `am start` 로 내비 앞으로 | 우리 앱의 프로브 액티비티가 맨 위면 "delivered to currently running top-most instance" 로 내비가 앞으로 오지 않음(`MainActivity` 가 맨 위일 때는 됨) |
| Virtual Console | 충전 상태(EV_CHARGE_STATE)·커넥터 연결 항목 없음(`charge_switch`·`charge_port_open` 만). 충전 중 시나리오는 재현 불가 |
| 빌드 | `ro.build.type=user`, `adb root` 불가. non-exported 컴포넌트는 adb 로 부를 수 없어 debug 매니페스트에서만 export |

## 8. 공공데이터 API 실측 (0-4, 2026-11-04)

`scripts/keco-probe.ps1`(개발계정 키, 15회 호출)로 쟀습니다. 키를 지운 응답은 `server/src/test/resources/fixtures/`에 있고 서버 파서 테스트가 읽습니다.

| 확인 | 결과 |
| --- | --- |
| 응답 형식 | `dataType=JSON`이면 평면형 JSON `{resultCode, resultMsg, totalCount, pageNo, numOfRows, items: {item: [...]}}`이고 값은 모두 문자열. `dataType`을 빼면 XML. 두 형식의 행 순서는 같음 |
| 행 수 | `numOfRows=1`을 줘도 10행(최소 10행). `item`이 단건 객체로 오는 경우는 보지 못함 |
| 필드 | 충전기 정보는 38개. 서버가 쓰는 `statId`·`chgerId`·`chgerType`·`output`·`lat`·`lng`·`stat`·`statUpdDt`·`lastTsdt`·`lastTedt`·`nowTsdt`·`busiId`·`busiNm`·`zcode`·`zscode`·`limitYn`·`delYn`이 모두 있음. 상태 변경분은 8개(`busiId statId chgerId stat statUpdDt lastTsdt lastTedt nowTsdt`)이고 `zcode`는 없음 |
| 시간 형식 | KST `yyyyMMddHHmmss` |
| 서울(zcode 11) | 충전기 77,042대. 9,999행 한 페이지가 7.95 MB, 1.4초 |
| 강원·전북 | 특별자치도 코드 51(18,055대)·52(18,214대)에 데이터가 있음. 옛 코드 45는 0행, 42는 57초 뒤 HTTP 503 |
| 시군구(`zscode`) 조건 | 30초 뒤 HTTP 504(`SERVICETIMEOUT_ERROR`, 코드 05). 쓸 수 없음 |
| 상태 변경분 | `zcode` 없이 전국 조회가 됨. 5분 창 9,824행(서울만 752행), 10분 창 15,538행. 9,999행 한 페이지가 1.58 MB, 0.3초. 상태가 그대로여도 `statUpdDt`만 새로 찍히는 행이 많음 |
| 오류 형태 | 게이트웨이 오류는 HTTP 오류 상태와 함께 JSON 봉투 `{"OpenAPI_ServiceResponse":{"cmmMsgHeader":{errMsg, returnAuthMsg, returnReasonCode}}}`로 옴. 잘못된 키는 403 + 30(`SERVICE_KEY_IS_NOT_REGISTERED_ERROR`), 시간 초과는 504 + 05 |

반영한 것:
- 서버 시도 코드에서 42·45를 뺐습니다. 42가 남아 있으면 503 때문에 전체 동기화가 매번 실패해 서버가 데이터를 한 번도 받지 못합니다.
- 파서가 JSON 오류 봉투를 알아봅니다. 전에는 HTTP 200으로 온 한도 초과 봉투를 빈 페이지로 읽을 수 있었고, 403·504는 원인 코드 없이 `HTTP_403`처럼만 남았습니다.
- 상태 조회는 계속 zcode 없이 합니다(`STATUS_PER_ZCODE` 불필요). 5분 창 9,824행은 한 페이지 한도(9,999행)에 가까워 붐비는 시간에는 2페이지가 됩니다.
- 실제 전체 동기화는 59페이지·약 11분이라 상태 변경분 창(10분)보다 깁니다. 전에는 끝나자마자 "공백"으로 보고 전체 동기화를 끝없이 반복했습니다. 이제 끝난 즉시 따라잡기 조회를 하고 연결을 9분 전부터 다시 시작합니다.
- 게이트웨이 시간 초과(30초 뒤 504)가 호출 다섯 번에 한 번꼴이라 호출마다 재시도를 3번에서 5번으로 늘렸습니다.
- 서울→부산 추천에 용인처인휴게소 양쪽(세종·포천방향)이 함께 나와, 실제 방향 표기 183곳을 보고 방향 표에 10곳을 더했습니다.

아직 못 본 것: `chgerType` 08~10의 의미(이번 표본은 06뿐), 늦게 보고된 변경이 period 창에서 빠지는지, 시간대별 변경분 행 수.
