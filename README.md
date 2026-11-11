# EV Companion · DriveCoach — Pleos Connect 차량용 앱

현대자동차그룹 차량용 앱 개발 공모전 출품작 저장소입니다. Pleos Connect(현대차그룹 AAOS 인포테인먼트)용 앱 두 개와
그 앱들이 쓰는 서버가 한 Gradle 프로젝트에 들어 있고, 두 앱은 차량 공통 코드(`:core-vehicle`)를 함께 씁니다.

| 앱 | 한 줄 소개 | 모듈 | APK |
| --- | --- | --- | --- |
| **EV Companion** | 내비가 고른 충전소를 실시간으로 점검해, 만차·고장이면 주행 중에도 대안을 먼저 알려 주는 전기차 충전 도우미 | `:app` (+ `:shared`, `:server`) | `apk/팀명_EVCompanion_release.apk` |
| **DriveCoach (드라이브 코치)** | 차량 신호로 운전 습관을 기록해 리포트로 코칭하고, 내 차 제원과 실제 핸들로 주차를 미리 연습하는 운전 코치 | `:drivecoach` (+ `:share-server`) | `apk/팀명_DriveCoach_release.apk` |

- EV Companion 상세: [docs/ADVISOR_APP.md](docs/ADVISOR_APP.md), [docs/ADVISOR_SERVER.md](docs/ADVISOR_SERVER.md), [docs/SPIKE_NAVI_EV.md](docs/SPIKE_NAVI_EV.md)
- DriveCoach 상세: [docs/DRIVECOACH.md](docs/DRIVECOACH.md)
- Pleos Connect SDK 정리: [docs/PLEOS_SDK_GUIDE.md](docs/PLEOS_SDK_GUIDE.md), 등록·심사·정책 요약: [docs/PLEOS_APP_POLICY_SUMMARY.md](docs/PLEOS_APP_POLICY_SUMMARY.md)

## 테스트 환경

| 항목 | 값 |
| --- | --- |
| 에뮬레이터 | **Pleos Connect Emulator v2.1.0** (Android 14 / API 34, 시스템 이미지 `system-images;android-34;pleos_car_emulator;x86_64`, 빌드 `RELEASE.release_connect_v2.1.0`) |
| 화면 규격 | 디바이스 프로필 "Pleos Connect v2.1.0": **2560×1440, 160 dpi(mdpi), 가로**. 앱 창은 가로 1682×1210(앱 하나가 앱 영역 전체), 세로 분할 841×1210 |
| AVD 설정 | Boot option **Cold boot**(Quick boot 는 VHAL 을 쓸 수 없음), RAM 4 GB, Graphics Hardware |
| 차량 상태 입력 | Pleos Connect Virtual Console (`adb forward tcp:8088 tcp:8088` → http://127.0.0.1:8088/) |
| SDK | Pleos Connect Vehicle SDK 2.3.2, NaviHelper SDK 3.0.5, AAOS 표준 Car API(CarPropertyManager, CarUxRestrictions) |
| 빌드 도구 | Android Studio 2026.x(번들 JBR), Gradle 9.7.1(wrapper), AGP 9.4.1, Kotlin 2.4.21, compileSdk 37 / targetSdk 34 / minSdk 28 |

실차에서는 검증하지 않았습니다. 모든 확인은 위 에뮬레이터에서 했습니다.

## 실행 방법

에뮬레이터를 Cold boot 로 띄운 뒤 APK 를 설치합니다.

```bash
adb install -r apk/팀명_EVCompanion_release.apk
adb install -r apk/팀명_DriveCoach_release.apk
```

### EV Companion

1. 앱을 실행하고 "자동차 정보에 액세스" 권한을 허용합니다(정차 중에만 물어봅니다). 대시보드에 **차량 연결됨**과 배터리 잔량, 주행 가능 거리, 충전 상태가 나옵니다.
2. 충전 도우미(실시간 충전소 추천)는 중계 서버가 필요합니다. 공개 서버를 아직 배포하지 않아, 제출 APK 는 `http://127.0.0.1:8080` 의 서버를 씁니다. PC 에서 서버를 띄우고 포트를 넘겨 줍니다.
   ```powershell
   $env:FAKE_SOURCE = "1"; .\gradlew.bat :server:run     # 키 없이 합성 충전소 데이터로 실행
   adb reverse tcp:8080 tcp:8080
   ```
   실제 한국환경공단 데이터로 실행하려면 data.go.kr 서비스 키가 필요합니다([docs/ADVISOR_SERVER.md](docs/ADVISOR_SERVER.md)).
3. Virtual Console 의 Energy 카드에서 Remaining Range 210 km, Battery SOC 42 %, Battery Energy 32.5 kWh 로 맞춥니다(스크립트: `pwsh -File scripts/pleos-vc.ps1 -RangeKm 210 -Soc 42 -BatteryKwh 32.5`).
4. 순정 내비에서 먼 목적지(예: 부산역)를 잡으면 내비가 충전 경유지를 스스로 넣습니다. EV Companion 의 **충전 도우미**를 열고 "동의하고 사용"을 누르면 "내비가 고른 ○○휴게소: 급속 1/8 이용 가능"과 대안 카드가 나옵니다.
5. 합성 데이터 서버에서는 `POST http://127.0.0.1:8080/debug/status-override` 로 충전소를 만차로 바꿔 "빈 충전기가 없어요 → 대안 → 내비에서 찾아 추가" 흐름을 볼 수 있습니다([docs/ADVISOR_APP.md](docs/ADVISOR_APP.md) E2E 절).

서버가 없어도 대시보드는 그대로 동작하고, 충전 도우미는 "충전 정보 서버에 연결할 수 없어요"와 내비 충전소 목록 버튼을 보여 줍니다.

### DriveCoach

1. 앱을 실행해 동의 화면에서 "동의하고 시작"을 누르고 권한 두 개를 허용합니다. 차량 속도 권한은 AAOS 에서 위치 권한 그룹이라 "위치 정보 액세스"로 표시됩니다(GPS 위치는 읽지 않습니다). 홈에 차량 신호(속도·기어·브레이크·조향·크루즈) 연결 상태가 나옵니다.
2. Virtual Console 로 주행합니다. 기어를 D 로 두고 몇 초 뒤 속도를 넣으면 주행이 기록되고, P 로 돌려 3분이 지나면 주행이 마감되어 리포트가 생깁니다.
   ```powershell
   pwsh -File scripts/pleos-vc.ps1 -Gear D
   pwsh -File scripts/pleos-vc.ps1 -SpeedKph 30          # 속도는 정수 km/h
   pwsh -File scripts/pleos-vc.ps1 -BrakePct 100 -SpeedKph 10
   pwsh -File scripts/pleos-vc.ps1 -Gear P -SpeedKph 0 -BrakePct 0 -SteeringDeg 0
   ```
3. 주차 연습은 동의하지 않아도 쓸 수 있습니다. P 단에서 핸들 연습을 켜고 Virtual Console 의 Steering Angle 을 바꾸면 예상 경로가 따라 움직입니다.
4. "폰으로 보기"는 공유 서버 주소가 들어간 빌드에서만 보입니다. 공개 서버를 아직 배포하지 않아 제출 APK 에서는 숨겨져 있고, 로컬 서버로 확인하는 방법은 [docs/DRIVECOACH.md](docs/DRIVECOACH.md)에 있습니다.

## 제출 APK 구성

| APK | 빌드 | 설명 |
| --- | --- | --- |
| `apk/팀명_EVCompanion_release.apk` | `:app:assembleStaging` 결과를 release 키로 서명 | release 와 같은 코드(R8, 비디버그, 디버그 도구 없음)에 중계 서버 주소만 로컬(`http://127.0.0.1:8080`) |
| `apk/팀명_DriveCoach_release.apk` | `:drivecoach:assembleRelease` 결과를 release 키로 서명 | 공유 서버 주소가 없어 "폰으로 보기" 버튼만 숨겨짐. 나머지는 모두 차 안에서 동작 |

```powershell
pwsh -File scripts/submission/make-apks.ps1 -Team "팀명"     # 빌드 + 서명 + apk/ 에 복사
```
서명 키와 비밀번호는 저장소에 없습니다(`local.properties` 의 `release.*`, [docs/DEPLOY.md](docs/DEPLOY.md)).

## 소스에서 빌드

PowerShell 에서 실행합니다(프로젝트 경로에 쉼표가 있으면 cmd·Git Bash 에서는 `gradlew.bat` 이 동작하지 않습니다).

```powershell
$env:JAVA_HOME = "<Android Studio>\jbr"
.\gradlew.bat :app:assembleDebug :drivecoach:assembleDebug        # 디버그 APK
.\gradlew.bat :app:testDebugUnitTest :drivecoach:testDebugUnitTest :shared:test :server:test :share-server:test
```
단위 테스트는 223개입니다(앱 61, 서버 52, 공용 27, DriveCoach 67, 공유 서버 16).

## Pleos Connect Emulator 준비

GUI 절차는 `docs/PLEOS_SDK_GUIDE.md` §1.2. CLI 는 아래 스크립트를 **일반 PowerShell 7 창**에서 실행합니다
(고전 `sdkmanager` = cmdline-tools 19.0 을 SDK 에 함께 설치 → 업데이트 사이트 등록 → 이미지 4.1 GB 다운로드 → Automotive AVD 생성):
```powershell
pwsh -ExecutionPolicy Bypass -File scripts/setup-pleos-emulator.ps1
```
- 42dot SDK 라이선스 프롬프트가 뜨면 내용을 읽고 `y` 를 입력합니다. 이미 동의했다면 `-AcceptPleosLicense` 로 자동 입력합니다.
- cmdline-tools 22.0 이상의 `sdkmanager` 는 사용자 업데이트 사이트를 읽지 않으므로(Android CLI 래퍼) 스크립트가 19.0 을 따로 씁니다.
- AVD 는 이미지에 동봉된 디바이스 프로필 **Pleos Connect v2.1.0**(id `Pleos_Connect_v2_0`, 2560×1440 mdpi, 가로)으로 만들고 Cold boot·RAM 4 GB·GPU host 로 설정합니다.
- 설치 결과: 이미지 약 18.3 GB(`system.img` 17.4 GB), 라이선스 파일 `Sdk\licenses\pleos-connect-sdk-license`, AVD `Pleos_Connect_v2_1`.
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd Pleos_Connect_v2_1 -no-snapshot
```

### Virtual Console (차량 상태 조작)
```bash
adb forward tcp:8088 tcp:8088
```
→ http://127.0.0.1:8088/ **Emulation Controls > Energy** (Battery SOC, Remaining Range, Charge Port …).
웹 UI 가 내부적으로 쓰는 HTTP API(`GET /vehicle/catalog`, `GET /vehicle/state`, `PUT <write_endpoint>?…`)를 그대로 호출하는 래퍼도 있습니다:
```powershell
pwsh -File scripts/pleos-vc.ps1 -Soc 42 -RangeKm 350          # SOC 42 %, 주행 가능 거리 350 km
pwsh -File scripts/pleos-vc.ps1 -ChargePort open -ChargeSwitch on
pwsh -File scripts/pleos-vc.ps1 -State                        # 현재 상태 요약 (-Raw: 전체 JSON)
pwsh -File scripts/pleos-vc.ps1 -Catalog                      # 카드 ↔ endpoint ↔ car property 목록 (63 개)
```
쓰기 API 는 웹 페이지가 1초마다 보내는 브라우저 세션 하트비트(`POST /browser/session?client=<id>`, 타임아웃 3초)가 살아 있을 때만 받고 아니면 503 `emulator_inactive` 를 돌려줍니다. 스크립트는 쓰기 직전에 하트비트를 직접 보내며, `-Hold 30` 을 붙이면 30초 동안 활성 상태를 유지합니다(웹 UI 를 열어 두면 UI 가 대신 보냅니다).

### 에뮬레이터 검증 결과 (Pleos Connect Emulator v2.1.0)

| 항목 | 결과 |
| --- | --- |
| Vehicle Service 연결 | 성공. `checkEvBatteryStateOfChargeCapabilities` 가 첫 시도에 `accessType=READ` 로 응답(초기화 후 약 2초). |
| Pleos SDK 초기값 | SOC 100 %, 충전 스위치 Off, 누적 주행거리 0 km (에뮬레이터 기본값). |
| 에뮬레이터 미지원 (SDK 호출 실패) | 현재 전비 `TRIP_EV_EFFICIENCY`, 충전 후 전비 `SINCE_CHARGE_EV_EFFICIENCY`, 트립 거리/시간 `TRIP_DRIVING_DISTANCE/TIME`, 드라이브 모드 `DRIVING_MODE` → `ERROR_NOT_SUPPORTED_PROPERTY` 또는 `IllegalArgumentException: property ID is not in carPropertyConfig list`. 앱은 해당 카드를 `—` 로 둡니다. |
| AAOS CarPropertyManager | `CAR_ENERGY` 허용 후 RANGE_REMAINING 500 km, EV_BATTERY_LEVEL 77.4 kWh, INFO_EV_BATTERY_CAPACITY 77.4 kWh, 충전 포트 닫힘/커넥터 분리 수신. 허용 전에는 `SecurityException` 과 `registerCallback: propertyId is not in carPropertyConfig list`. (EV_CHARGE_STATE 는 SOC와 무관하게 늘 충전 완료라, 커넥터가 분리돼 있으면 "대기"로 표시) |
| 실시간 갱신 | Virtual Console 로 SOC 42 %·Range 350 km 설정 → 즉시 `onChangeEvent(EV_BATTERY_SOC=42)` 수신, 게이지·주행 가능 거리·클러스터 갱신. |
| 제출 APK (2026-11-11) | 두 APK 모두 설치·실행 확인. EV Companion: 차량 연결, 로컬 서버(합성 데이터)로 경로의 충전소 점검과 대안 카드 3장, 내비 검색 연동. DriveCoach: 동의·권한 뒤 차량 신호 5종 연결, 기록 서비스 실행. |
| 참고 | `PERF_ODOMETER` 변경 이벤트가 값이 같아도 3~4초마다 반복 수신됩니다(불필요한 리컴포지션 주의). |

## 구조
```
app/src/main/java/com/example/pleosev/                EV Companion
├─ MainActivity.kt                 Compose 진입 + 런타임 권한, 대시보드 ↔ 충전 도우미 화면
├─ EvApp.kt, di/AppGraph.kt        프로세스 범위 객체(차량·내비·충전 도우미, 수동 DI)
├─ model/EvDashboardState.kt       화면 상태
├─ data/PleosVehicleRepository.kt  Pleos Vehicle SDK (EvBattery / Odometer / DrivingMode)
├─ data/FakeVehicleRepository.kt   데모 데이터 (디버그 전용 폴백)
├─ data/VehicleStateHolder.kt      차량 연결·폴백 (대시보드와 충전 도우미가 공유)
├─ data/remote/, data/settings/    중계 서버 클라이언트, 충전 도우미 설정(DataStore)
├─ car/CarPropertySource.kt        AAOS CarPropertyManager (RANGE_REMAINING, EV_BATTERY_LEVEL, 충전 포트/상태)
├─ navi/                           순정 내비 연동 (NaviHelper)
├─ advisor/                        충전 필요 판단·추천·내비 동작 (ChargingAdvisor + domain/), 백그라운드 감시·알림 (AdvisorMonitor + background/)
└─ ui/                             DashboardViewModel, DashboardScreen, advisor/AdvisorScreen, theme
core-vehicle/src/main/java/com/example/pleos/core/   (:app 과 :drivecoach 공용 라이브러리)
├─ PleosErrors.kt                  SDK 오류 코드 추출
└─ car/UxRestrictionsMonitor.kt    주행 규제(CarUxRestrictions) → 주행 중 간소화 UI, CarBridge
drivecoach/   DriveCoach 앱 (vehicle · record · analysis · parking · data · share · ui, 구조는 docs/DRIVECOACH.md)
shared/       앱과 중계 서버가 함께 쓰는 DTO·점수 계산
server/       충전소 중계 서버 (Ktor)            share-server/   DriveCoach "폰으로 보기" 공유 서버 (Ktor)
deploy/       중계 서버 Docker Compose + Caddy    scripts/        에뮬레이터 설치, Virtual Console, 제출물 스크립트
```

## 충전 도우미 (EV Companion)
순정 내비(NaviHelper)와 연동해, 충전이 필요할 때 실시간 충전소 현황(한국환경공단 API)을 보고 추천합니다.
- `:shared` — 앱과 서버가 함께 쓰는 API DTO·점수 계산·이름 매칭·방향 판별 (Kotlin JVM, JVM 17 바이트코드)
- `:server` — 공공데이터 API를 캐시하고 충전소를 추천하는 Ktor 중계 서버, 기본 127.0.0.1:8080. 키 없이 합성 데이터로 실행: `$env:FAKE_SOURCE = "1"; .\gradlew.bat :server:run`. 실행·API·동기화·추천 로직은 [docs/ADVISOR_SERVER.md](docs/ADVISOR_SERVER.md)
- 앱(`app/`) — 순정 내비 연동, 충전 필요 판단, 추천 카드(정차·주행 레이아웃), 대시보드 배너, 내비가 앱을 가려도 계속 감시(`specialUse` 포그라운드 서비스)하고 버튼 달린 알림으로 알리기. 구조·판단 규칙·알림 규칙·에뮬레이터 E2E 결과는 [docs/ADVISOR_APP.md](docs/ADVISOR_APP.md)
- 진행: 백그라운드 감시·알림까지 에뮬레이터 + 합성 데이터로 확인했습니다. 알림 게시와 버튼 동작은 확인했지만, 에뮬레이터에서는 헤드업 카드가 그려지지 않아(Pleos 시스템 쪽, [SPIKE §7](docs/SPIKE_NAVI_EV.md)) 실차나 Pleos 문의로 확인해야 합니다. 2026-11-04 개발계정 키로 실제 공공데이터 API를 확인했습니다(전체 동기화 약 12분, DC 충전소 21,740곳, [SPIKE §8](docs/SPIKE_NAVI_EV.md)). 같은 날 [Virtual Console 가이드](https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide)의 카드로 앱을 실데이터 서버와 함께 점검하고 찾은 문제 6가지를 고쳤습니다([ADVISOR_APP.md](docs/ADVISOR_APP.md) "Virtual Console 가이드").
- 배포·출시 준비: 서버 Docker 이미지 + Caddy(HTTPS), release 서명·R8, R8 확인용 `staging` 빌드(`.\gradlew.bat :app:assembleStaging`), 마켓 업데이트 팝업, CCP Back 숨김, 처리방침·위치약관 초안, 운영자가 할 일 목록은 [docs/DEPLOY.md](docs/DEPLOY.md)
- `app/src/debug/.../debug/` — NaviHelper·알림·FGS 실측 프로브 (debug 빌드 전용)
- 실측 결과: [docs/SPIKE_NAVI_EV.md](docs/SPIKE_NAVI_EV.md), 공공데이터 API 실측 스크립트: `scripts/keco-probe.ps1` (`deploy/advisor.env`의 `DATA_GO_KR_SERVICE_KEY`, 또는 같은 이름의 환경변수 필요. 키는 저장소에 없습니다)

## 드라이브 코치 (DriveCoach, `:drivecoach`)
운전 습관(급가속·급제동, 액셀·브레이크 사용, 회생제동 활용)을 차량 안에서 암호화해 기록하고 주행 후 리포트·코칭을 보여 주며,
후진 직각·평행·전진 직각·사선 주차를 시뮬레이션하고 P단에서 실제 핸들로 연습하는 앱입니다.
"폰으로 보기"는 리포트를 차에서 암호화해 공유 서버(`:share-server`, Ktor, 포트 8090)에 7일 올리고 QR로 폰 브라우저에서 엽니다.
설명·신호·권한·에뮬레이터 실측·Virtual Console 레시피·폰으로 보기 실행법: [docs/DRIVECOACH.md](docs/DRIVECOACH.md)
```powershell
.\gradlew.bat :drivecoach:testDebugUnitTest :drivecoach:assembleDebug :share-server:test
```

## 선언된 퍼미션 (EV Companion, 사용 도메인만)
| 퍼미션 | 용도 |
| --- | --- |
| `pleos.car.permission.CAR_ENERGY` | EvBattery: SOC %, 전비, 충전 스위치 |
| `pleos.car.permission.CAR_DRIVE` | Odometer(ODO/트립), DrivingMode |
| `android.car.permission.CAR_ENERGY` (runtime) | AAOS RANGE_REMAINING, EV_BATTERY_LEVEL, 충전 전력/상태 |
| `android.car.permission.CAR_ENERGY_PORTS` | 충전 포트 열림/연결 |
| `android.car.permission.CAR_INFO` | INFO_EV_BATTERY_CAPACITY |
| `pleos.car.permission.NAVI_ROUTE` / `NAVI_ROUTE_SEARCH` / `NAVI_CUSTOM_MAP` | NaviHelper: 경로·경유지 조회, 경유지 추가, 내비 화면 열기 (충전 도우미) |
| `android.permission.FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | 충전 도우미 백그라운드 감시(내비가 앱을 가린 동안 프로세스 동결 방지) |
| `android.permission.POST_NOTIFICATIONS` (runtime) | 충전 추천 알림·감시 중 상시 알림 (정차 중 도우미 화면에서 요청) |

충전 제어(`setEvChargeSwitch` 등, `CONTROL_CAR_ENERGY`)는 쓰지 않습니다.
VIN(`getVINInfo`)은 민감한 차량 정보라 읽지 않으며, 그래서 `pleos.car.permission.CAR_INFO` 도 선언하지 않습니다.
DriveCoach 의 신호와 권한은 [docs/DRIVECOACH.md](docs/DRIVECOACH.md) "차량 신호와 권한" 표에 있습니다.

## 다음 단계
- 중계 서버·공유 서버의 HTTPS 공개 배포, 화면 디자인 적용
- `onChargerOperatorInfo`(운영사 목록)로 충전 도우미의 운영사 선호/회피 설정
- FusedLocation 근접 알림으로 충전소 도착 감지, TextToSpeech 음성 안내
- 앱 ID `com.example.pleosev`·`com.example.drivecoach` 를 실제 패키지명으로 변경 (마켓 제출용, [docs/DEPLOY.md](docs/DEPLOY.md) "운영자가 할 일")
