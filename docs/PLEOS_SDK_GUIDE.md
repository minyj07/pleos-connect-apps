# Pleos Connect SDK 학습 정리 (EV 앱 개발용)

> 원문: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/ 및 하위/연관 문서 전체 (2026-10-28 기준, SDK 릴리즈 노트 2026-10-19).
> 로컬 미러: `docs/pleos-docs/guides/` (가이드 103쪽), `docs/pleos-docs/api-reference/` (API 레퍼런스 1,800여 쪽).
> 등록·심사·정책 문서 요약: `docs/PLEOS_APP_POLICY_SUMMARY.md`.

---

## 0. 한눈에 보기

| 항목 | 내용 |
| --- | --- |
| 플랫폼 | **Pleos Connect** = 현대차그룹(42dot) 차량 인포테인먼트, **Android Automotive OS(AAOS)** 기반. 앱은 일반 Android 앱(Kotlin/Java)이며 차량 기능은 **Pleos Connect SDK**로 접근 |
| SDK 모듈 (최신) | **Vehicle 2.3.2**, **NaviHelper 3.0.5**, **FusedLocation 1.1.3**, **TextToSpeech 3.3.1**, **SpeechToText 3.3.1** (LLM SDK는 2026-10-19 제공 종료) |
| Maven | `https://nexus-playground.pleos.ai/repository/maven-releases/` (groupId `ai.pleos.playground`, 인증 없이 접근 가능 확인) |
| 에뮬레이터 | **Pleos Connect Emulator v2.1.0** (Android 14 / API 34, x86_64 Automotive 이미지, 약 4 GB). 라이선스: 42dot SDK License |
| 테스트 도구 | **Virtual Console** (`adb forward tcp:8088 tcp:8088` → http://127.0.0.1:8088/) 로 배터리·충전·주행 상태를 시뮬레이션. 폼팩터 테스트(Host Display Bridge) 제공 |
| 필수 조건 | `minSdk >= 28` (서명키 로테이션/리니지 요건), Gradle 8.0+, Kotlin/Java |
| API 설계 원칙 | **AAOS Alignment**: AAOS 표준 API(CarPropertyManager 등)에 있는 기능은 표준 API 사용, SDK는 제조사 특화 기능만 제공. 표준 편입 시 최소 2개 메이저 버전 하위 호환 |
| 퍼미션 정책 | `pleos.car.permission.*` 는 **매니페스트 선언형**(런타임 팝업 없음). 사용하는 도메인의 최소 권한만 선언. 미승인 권한 선언/호출 시 **앱 심사 탈락** |
| 지원 여부 확인 | Vehicle SDK: 도메인별 `checkXxxCapabilities` / FusedLocation: `isSupported()` / NaviHelper: 내비 탑재 차량만 |

---

## 1. 개발 환경

### 1.1 요구 사항 (문서 기준)
- Android Studio 최신, Android SDK API 34, 프로젝트 `minSdk` 28 이상, Gradle 8.0 이상.
- 에뮬레이터용 GPU: 권장 RTX 4060/5060 Laptop 8GB급 이상, 최소 RTX 3060 Laptop 6GB / RX 6600M 8GB.
- 이 PC 상태 (2026-10-28 확인): Android Studio 2026.2.1 (`D:\Android Studio`, JBR 25), SDK `%LOCALAPPDATA%\Android\Sdk` (platforms 36·37.0, build-tools 35/36, emulator 37.2.12, platform-tools 37.0.1), JDK 21(PATH), Gradle 캐시 8.11.1/9.3.1, 하이퍼바이저 사용 가능, RAM 31 GB. **2026-10-29 설치·검증 완료: cmdline-tools 23.0 + 19.0, Pleos 시스템 이미지(`system-images;android-34;pleos_car_emulator;x86_64`, 설치 후 약 18.3 GB), AVD `Pleos_Connect_v2_1`**. API 34 platform(`platforms;android-34`)은 여전히 없지만 compileSdk 37 이라 빌드에 불필요. GPU: NVIDIA GeForce RTX 5070 Laptop(권장 사양 충족) + WHPX 가속.

### 1.2 Pleos Connect Emulator 설치 (Android Studio GUI)
1. **Tools > SDK Manager > SDK Update Sites > +**
   - Name: `Pleos Connect System Image`
   - URL (Windows/Linux): `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
   - URL (Mac arm64): `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
2. **SDK Platforms** 탭 → **Show Package Details** → API 34 아래 **Pleos Connect v2.1.0 APIs** (패키지 `system-images;android-34;pleos_car_emulator;x86_64`, 4.09 GB) 설치 → 42dot 라이선스 동의 → Android Studio 재시작.
3. **Tools > Device Manager > Create device > Automotive** → `Pleos Connect v2.1.0` 선택 → Advanced: **Boot option = Cold boot(필수, Quick boot면 VHAL 불가)**, **RAM 4 GB**, Graphics Hardware → API 34 `Pleos Connect v2.1.0 APIs` 선택 → Finish.
4. 기존 버전 이미지가 있으면 삭제 후 설치. 블루투스 동글 연동 미지원. 기본 언어 한국어(차량 설정 > 일반 설정 > 언어에서 변경).
5. STT 테스트 시 Extended Controls > Microphone 에서 오디오 입력 허용.

CLI 대안: `scripts/setup-pleos-emulator.ps1` (일반 PowerShell 7 창에서 실행). 동작: 고전 `sdkmanager`(cmdline-tools 19.0)를 `Sdk\cmdline-tools\19.0`에 설치 → `~/.android/repositories.cfg`에 업데이트 사이트 등록 → 이미지 설치(42dot 라이선스 프롬프트에 직접 `y`, 동의했다면 `-AcceptPleosLicense`로 자동 입력) → AVD 생성.
  - 2026-10-28 확인: cmdline-tools 22.0 이상의 `sdkmanager.bat`는 새 Android CLI(`android sdk`)로 넘기는 래퍼이고, 이 CLI는 사용자 업데이트 사이트(`repositories.cfg`)를 읽지 않아 Pleos 패키지가 목록에 나오지 않는다. 그래서 19.0을 `latest` 옆에 두고 이미지 설치에만 쓴다. `avdmanager.bat`는 23.0 것도 아직 고전 도구라 그대로 쓴다.
  - `repositories.cfg` 키 형식(파서 `LocalSourceProvider` 기준): `count=1`, `src00=https\://…/crp-sys-img.xml`, `disp00=Pleos Connect System Image`, `enabled00=true`.
  - MSIX 앱 패키지 ID를 상속한 프로세스(예: Claude Code의 Bash 도구)에서 실행하면 `%LOCALAPPDATA%` 쓰기가 `AppData\Local\Packages\<앱>\LocalCache\Local`로 리다이렉트되어 Android Studio가 설치 결과를 보지 못한다. 일반 PowerShell 창(또는 Claude Code의 PowerShell 도구)에서 실행할 것.
  - AVD 디바이스 프로필: 이미지 폴더의 `devices.xml` 에 **Pleos Connect v2.1.0**(id `Pleos_Connect_v2_0`, 2560×1440, 160 dpi, 가로, 제조사 42dot)이 정의되어 있어 `avdmanager list device` 에 나타난다(Android Studio 의 Automotive > Pleos Connect v2.1.0 과 동일). 스크립트는 이 id 를 우선 쓰고 없으면 `automotive_1024p_landscape` 로 대체한다. 이미지 `source.properties` 는 `hw.ramSize=8192` 를 제안하지만 문서대로 4 GB 로 둔다.
  - 설치 결과(2026-10-29): `system.img` 17.4 GB 등 약 18.3 GB, `Sdk\licenses\pleos-connect-sdk-license` 생성, 빌드 `Pleos/crp_avd/crp_avd_x86_64:14/RELEASE.release_connect_v2.1.0`(모델 `Connect on x86_64 emulator`, 42dot). 실행 `emulator.exe -avd Pleos_Connect_v2_1 -no-snapshot`, 콜드 부팅 약 40초. 에뮬레이터 시계가 호스트와 달라 logcat 날짜가 다르게 찍힐 수 있다.

### 1.3 Virtual Console (차량 상태 시뮬레이터)
- 에뮬레이터 실행 후 `adb forward tcp:8088 tcp:8088` → 브라우저 `http://127.0.0.1:8088/`.
- **Energy** 도메인 카드: Remaining Range(0~1,000,000 m, 1,000 m 단위), Battery Energy(0~200 kWh, 0.1), **Battery SOC(0~100 %)**, Battery Capacity(0~200 kWh), Charge Current Limit(10~48 A, 0.5), Charge Percent Limit(50~100 %, 5), Charge Switch(On/Off), Charge Port(Open/Closed).
- Motion: Gear(P/R/N/D), Brake Pedal, Odometer(0~1,000,000 km), Steering. ADAS: Cruise state/target speed. Safety: Telltales(On/Off/Blink). Climate: HVAC 전반. Convenience: 도어/창문/시트/조명/와이퍼.
- 주행 규제 토글: `adb shell cmd hmg_car_service --service drivingstate driving_regulation on|off`.
- HTTP API(웹 UI 가 내부적으로 호출, 2026-10-29 실측): `GET /vehicle/catalog`(63 개 카드: `write_endpoint`, `control.field`/`params`, 매핑된 `car_property`), `GET /vehicle/state`(현재 상태 JSON: `ev_battery`, `odometer`, `gear`, `controls`, `body`, `hvac` …), 쓰기는 `PUT <write_endpoint>?<params>&<field>=<value>`(POST 도 동작). 래퍼: `scripts/pleos-vc.ps1`.
  - Energy: `PUT /vehicle/ev-battery?field=soc&value=42`(Battery SOC → vendor `EV_BATTERY_SOC` 0x21400BA7), `field=battery_level`(kWh → `EV_BATTERY_LEVEL`), `field=capacity`(→ `INFO_EV_BATTERY_CAPACITY`), `field=current_limit`(10~48 A → `EV_CHARGE_CURRENT_DRAW_LIMIT`), `field=percent_limit`(50~100 → `EV_CHARGE_PERCENT_LIMIT`), `field=charge_switch&value=true|false`(→ `EV_CHARGE_SWITCH`), `field=charge_port_open&value=true|false`(→ `EV_CHARGE_PORT_OPEN`), `PUT /vehicle/odometer?remaining_range_m=350000`(→ `RANGE_REMAINING`), `traveled_distance_km=`(→ `PERF_ODOMETER`), `usage_time_min=`(→ `TRIP_DRIVING_TIME`, 단 SDK 는 미지원 응답). Motion: `PUT /vehicle/gear?gear=Park|Reverse|Neutral|Drive`(→ `VENDOR_GEAR_SELECTION` 0x21400400), `PUT /vehicle/controls?brake=…`, `/vehicle/power-system/simulator?area_id=0&state=Off|Ready|On|Driving`.
  - 활성화 조건: 서버는 브라우저 세션 하트비트 `POST /browser/session?client=<id>`(응답 `{"active":true,"clients":n,"timeout_ms":3000}`)가 3초 안에 반복될 때만 각 섹션의 `emulator_active=true`/`bridge_ready=true` 로 두고 쓰기를 받는다. 없으면 503 `{"error":"emulator_inactive"}`, `availability.source=web-deactivation`. 웹 페이지는 1초마다 하트비트를, `pagehide` 때 `&closing=1` 을 sendBeacon 으로 보내 즉시 비활성화한다(브라우저 탭을 숨기면 타이머가 느려져 활성/비활성이 왔다갔다 할 수 있음). curl 로 직접 쓸 때는 하트비트 → 3초 안에 PUT. `scripts/pleos-vc.ps1` 은 쓰기 직전에 하트비트를 보내고 `-Hold N` 으로 N초 유지한다.
  - SOC 를 바꾸면 앱의 `EvBatteryStateOfChargeListener` 에 수 ms 내 `onChangeEvent(EV_BATTERY_SOC)` 가 들어오고, Range 는 AAOS `RANGE_REMAINING` 콜백으로 갱신된다(검증 스크린샷 `docs/screenshots/pleos-emulator-soc42.png`).
- 가이드대로 테스트하며 알게 된 동작(2026-11-04, 결과: `docs/ADVISOR_APP.md` "Virtual Console 가이드")
  - **콘솔은 자기 상태를 따로 들고 묶음으로 보낸다.** 콜드 부팅 직후 콘솔 값은 Remaining Range 0 m, Odometer 0 km, Charge Percent Limit 50 %, Current Limit 12.5 A다. 차량(VHAL) 기본값과 다르다(주행가능 500 km). `odometer` 묶음(누적 주행거리·주행 시간·주행가능 거리)은 한 값만 써도, 또 주행 시뮬레이터가 움직여도 나머지를 콘솔 값으로 함께 보낸다. 그래서 차량의 500 km가 0 km로 바뀐다. 테스트를 시작할 때 Remaining Range부터 정한다.
  - **누적 주행거리는 늘리기만 된다.** 더 작은 값은 400 `{"error":"traveled_distance_must_not_decrease"}`. 0으로 되돌리려면 콜드 부팅한다.
  - **메인 화면의 주행 시뮬레이터(ACCEL/BRAKE)가 속도를 직접 보낸다.** 차가 움직이는 동안 초당 약 7번 `source=vehicle-controls action=web-set speed_kph=…`(logcat `VehicleApiDdsEmu`)을 보내 API로 넣은 속도를 덮어쓰고 감속시킨다. ACCEL이 눌린 상태로 남으면 기어 D, 10초 안에 240 km/h까지 오른다. API로 정확한 속도를 둘 때는 웹 페이지를 닫는다.
  - Emulation Controls 창은 1초마다 다시 그려져 접근성 참조(ref)가 곧바로 무효가 된다. 자동화는 좌표 클릭이나 HTTP API로 한다. 화면 하단 문구는 "48 features · 7 domains", `/vehicle/catalog` 는 63개(가이드에 없는 power-system·tire-pressure·seat 위치 카드 포함).
  - Battery Capacity(`INFO_EV_BATTERY_CAPACITY`)는 정적 속성이라 바꿔도 변경 이벤트가 없다(`getProperty` 는 새 값). `EV_CHARGE_STATE` 는 부팅 때부터 FULLY_CHARGED로 SOC와 무관하고, 이것과 `EV_CHARGE_PORT_CONNECTED` 를 바꾸는 카드는 없다(충전 중 상태는 재현 불가).
  - 기어 D면 0 km/h에서도 주행 UX 제한이 켜진다. Notification 1(Show)은 왼쪽 Driving View에 빨간 "차량 내 유독가스 감지! 즉시 정차하고 탈출하세요." 카드와 상단 알림 점을 띄우고, 앱 창·포커스는 그대로다.
  - 쓰기 엔드포인트 중 `PUT /vehicle/lights?feature=hazard&value=…` 는 400(파라미터 형식 미확인). 웹 UI의 Hazard 카드는 동작한다.

### 1.4 ADB 메모
- `adb devices`, `adb install -d -r app.apk`, `adb shell am start -n <pkg>/<Activity>`, `adb shell monkey -p <pkg> -c android.intent.category.LAUNCHER 1`, `adb exec-out screencap -p > shot.png`.
- 실행 플랫폼 구분(실차만 유효): `Build.DEVICE` = `CONNECT_S` / `CONNECT_L` / `CONNECT_W`. 지역: `product.sw.target_region` = `KOR` / `EUR` / `USA` / `CAN`.

---

## 2. Pleos Playground 계정·프로젝트 (사용자 작업 필요)

1. https://pleos.ai/playground 회원가입 → **Workspace > My Project > 프로젝트 생성** (이름, 개발사명, **카테고리**, 설명).
2. 프로젝트 정보: **프로젝트 ID(Key)** = 앱 심사 제출 시 App Market Console 입력값. **클라이언트 ID / 시크릿** = TTS·STT 서버 모드 `registerApp()`에 필요. CRN은 에뮬레이터 2.1.0부터 불필요.
3. **SDK 사용 신청**: 좌측 **SDK** 메뉴에서 상태 확인.
   - 신청 없이 사용 가능: **Vehicle State(조회)**, **NaviHelper**
   - 준비 중(Coming Soon, 신청 불가): FusedLocation, TextToSpeech, SpeechToText, **Vehicle Control(제어/set API)**
   - 제휴 계약 필요 항목은 별도 문의.
   → 당장 쓸 수 있는 것은 **Vehicle SDK 조회(get/register/capability)** 와 **NaviHelper**. 충전 제어(`setEvChargeSwitch` 등 `CONTROL_CAR_ENERGY`)는 승인 전까지 호출/선언 금지.

---

## 3. 프로젝트 설정 (이 저장소에 적용된 값)

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google(); mavenCentral()
        maven { url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/") }
    }
}
// app/build.gradle.kts
dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    // implementation("ai.pleos.playground:NaviHelper:3.0.5")
    // implementation("ai.pleos.playground:FusedLocation:1.1.3")
    // implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    // implementation("ai.pleos.playground:SpeechToText:3.3.1")
}
```

- 이 저장소: AGP 9.4.1 + Gradle 9.7.1 + Kotlin 2.4.21(빌트인 Kotlin, `org.jetbrains.kotlin.android` 플러그인 불필요) + Compose BOM 2026.09.00, `compileSdk 37 / targetSdk 34 / minSdk 28`. (최신 AndroidX는 compileSdk 37 이상 요구.)
- Vehicle AAR 의존성: kotlin-stdlib 1.9.25, vehicle-aidl 1.1.1, vehicle-data 1.1.11, core-ktx 1.8.0, coroutines 1.6.3, kotlin-reflect, gson 2.10.1. AAR 매니페스트가 `ai.pleos.playground.service.vehicle.VEHICLE_BINDING` 권한과 `<queries>`를 자동 병합. **Vehicle Service 최소 버전 2.3.13** 요구.
- 공식 샘플 앱 기준(참고): JDK 17, Gradle 8.10.2, AGP 8.8.2, Kotlin 2.0.0, compileSdk/targetSdk 35.
- ProGuard: `-keep class ai.pleos.playground.** { *; }` (IPC/Gson 모델 보호).
- AAOS `CarUxRestrictions`, `CarPropertyManager` 사용 시: `android { useLibrary("android.car") }` + 매니페스트 `<uses-library android:name="android.car" android:required="false"/>` (SDK platforms/android-3x/optional/android.car.jar 포함 확인됨).

---

## 4. Vehicle SDK (v2.3.2 / Vehicle Service v2.3.21~22)

### 4.1 구조와 생명주기
```kotlin
val vehicle = Vehicle(context)      // 진입점 (final class)
vehicle.initialize()                // 서비스 연결 (콜백 없음, 예외 없이 반환돼도 연결 완료 보장 X)
val ev = vehicle.getEvBattery()     // 도메인 객체는 getXxx()로만 획득 (직접 생성 불가)
// ... capability / get / set / register ...
vehicle.release()                   // onDestroy 등 사용 종료 시. 비동기 콜백 완료 전 호출 금지
```
- 도메인 getter: `getEvBattery, getHVAC, getDoor, getSeat, getWindow, getDisplay, getOdometer, getLight, getSafety, getCarInfo, getDrivingMode, getGear(비문서화·deprecated), getSteeringWheel, getToll, getTurnSignal, getWiper, getTire, getSideMirror, getBrake, getFuel`.
- 네 가지 API 타입: **Capability**(`checkXxxCapabilities(onComplete(config[, map]), onFailed)`) / **Get**(`getXxx(onComplete(value?), onFailed)`) / **Set**(`setXxx(value, onComplete = {}, onFailed = {})`) / **Callback**(`registerXxx(listener)` / `unregisterXxx(listener)` — 등록 때와 **같은 인스턴스·같은 area**로 해제).
- 모든 결과는 IPC 비동기 콜백(`(T?) -> Unit`, `(Exception) -> Unit`). 값 `null` = 사용 가능한 값 없음(오류 아님). 콜백 스레드는 보장되지 않으므로 UI 갱신은 메인으로 전환.
- `CapabilityConfig.accessType`: `NONE / READ / WRITE / READ_WRITE`. 영역형 API는 `Map<Area, AreaBounds<T>>(minValue, maxValue)` 동반. 2.3.2부터 `DoorArea/SeatArea/...`는 내용 기반 equals/hashCode.
- 요청 완료 콜백 ≠ 실제 값 변경. Set 이후에는 재조회하거나 리스너 값으로 확인(샘플 앱 가이드).

### 4.2 EV 앱에 필요한 도메인·API (단위/권한은 AAR 어노테이션·Dokka 기준)

**EvBattery** — `pleos.car.permission.CAR_ENERGY` (set 계열은 `CONTROL_CAR_ENERGY`)

| API | 타입 | 단위/값 | 비고 |
| --- | --- | --- | --- |
| `checkEvBatteryStateOfChargeCapabilities(onComplete:(CapabilityConfig), onFailed)` | capability | | 연결 확인용으로도 사용 |
| `getEvBatteryStateOfCharge((Int?)->Unit, (Exception)->Unit)` | get | **%** | 배터리 충전율 |
| `registerEvBatteryStateOfCharge(EvBatteryStateOfChargeListener)` / `unregister…` | callback | % | `onEvBatteryStateOfChargeUpdated(value: Int?)` |
| `getCurrentEvEfficiency / getInstantaneousEvEfficiency / getSinceChargeEvEfficiency / getCumulativeEvEfficiency ((Float?)->Unit, …)` | get | **km/kWh** | 현재 주행 / 순간 / 충전 후 / 누적 전비 |
| `register{Current,Instantaneous,SinceCharge,Cumulative}EvEfficiency(EvBatteryEfficiencyListener)` | callback | km/kWh | `onEvBatteryEfficiencyUpdated(efficiency: Float?)` |
| `getEvChargeSwitchState((Boolean?)->Unit, …)` | get | on/off | 충전 활성 상태 |
| `getEvChargeCurrentDrawLimit((Float?)->Unit, …)` / `checkEvChargeCurrentDrawLimitCapabilities((CapabilityConfig, List<Float>)->Unit, …)` | get/cap | **A** | 지원 전류 제한 목록 |
| `checkEvChargePercentLimitCapabilities((CapabilityConfig, List<Float>)->Unit, …)` | cap | % | 목표 충전량 후보 |
| `setEvChargeSwitch(Boolean, …)`, `setEvChargePortOpen(Boolean, …)`, `setEvChargeCurrentDrawLimit(Float, …)`, `setEvChargePercentLimit(Float, …)` | set | | **CONTROL_CAR_ENERGY** — Vehicle Control SDK 승인 전 사용 불가 |
| `getEvBatteryLevel`, `getEvBatteryCapacity`(CAR_INFO), `isEvChargePortOpen`, `registerEvBatteryLevel`, `registerEvChargePortOpen` | get/callback | Wh? | **deprecated·문서 비공개**(릴리즈 노트 "문서 숨김"). AAOS 표준 프로퍼티로 대체 → §8 |

**Odometer** — `pleos.car.permission.CAR_DRIVE`

| API | 단위 | 비고 |
| --- | --- | --- |
| `getCurrentDistanceDriven((Float?)->Unit, …)` / `registerDistanceDriven(DistanceDrivenListener)` | **km** | 누적 주행거리(ODO) |
| `getTripCurrentDrivingDistance` / `…Cumulative…` / `…SinceRefill…` / `…Instantaneous…(deprecated)` | **m** | 트립 거리. 리스너는 `DistanceDrivenListener`(단위는 등록 API 기준) |
| `getTripCurrentDrivingTime` / `…Cumulative…` / `…SinceRefill… ((Int?)->Unit, …)` | **분** | 리스너 `DrivingTimeListener.onDrivingTimeUpdated(value: Int?)` |
| `checkTripDrivingDistanceCapabilities`, `checkTripDrivingTimeCapabilities` 등 | | `TripStatistics<T>(current, instantaneous, cumulative, sinceRefill, raw)` 모델·`TripStatisticType` 존재 |
| `getRangeRemainingDistance`, `registerRangeRemainingListener`, `setRangeRemainingDistance(CONTROL_CAR_DRIVE)` | m | **deprecated** (set은 deprecated-with-error). AAOS `RANGE_REMAINING` 사용 |

**DrivingMode** — `CAR_DRIVE`: `getCurrentDrivingModeState((DrivingModeType?)->Unit, …)` → `NORMAL / ECO / SPORT`, `registerDrivingModeState(DrivingModeStateListener)`; `getCruiseControlState` → `CruiseControl { OTHER, ENABLED, ACTIVATED, USER_OVERRIDE, SUSPENDED, FORCED_DEACTIVATION_WARNING }`, `getCruiseControlTargetSpeed(Float)`.

**CarInfo** — `CAR_INFO`: `getVINInfo((String?)->Unit, …)`, `checkVINInfoCapabilities` (CRN 계열은 deprecated). `getCapabilityInfo`는 `PRIVATE_CAR_INFO`(3rd party 불가).

**Display** — `CAR_INFO`: `getCurrentTemperatureDisplayUnit` → `TemperatureUnit { CELSIUS, FAHRENHEIT }`, 리스너 `TemperatureDisplayUnitListener`. 거리/속도/배터리 단위 enum(`DistanceDisplayUnit {KILOMETER, MILE}`, `SpeedDisplayUnit`, `EvBatteryDisplayUnit {WATT_HOUR, AMPERE_HOURS, KILOWATT_HOUR}`)은 상수만 존재.

**Safety** — `CAR_SAFETY`: 경고등 `getTelltalesInfo((Telltales)->Unit)`(`TelltaleData(type: TelltaleType, state: OFF/ON/BLINK, color, priority, warningCount)`; EV 관련 타입 `EV_SYSTEM, LOW_EV_BATTERY, EV_CHARGING_CONNECTOR, EV_BATTERY_CONDITIONING, POWER_DOWN, RBS, XEV_READY, I_PEDAL …`). 단 `registerTelltales/checkTelltalesCapabilities`는 `PRIVATE_CAR_SAFETY`. `hasPowerDownWarningIndicator`, 타이어 `getLowTirePressure`.

**Fuel** — `CAR_ENERGY`: `getFuelPercent(Int %)`, 연비 4종(현재/순간/충전 후/누적) — HEV/PHEV용.

**Gear** (비문서화, deprecated): `getCurrentGearSelectionState` → `VehicleGear {GEAR_PARK, GEAR_DRIVE, GEAR_NEUTRAL, GEAR_REVERSE …}`. 기어/속도는 AAOS `GEAR_SELECTION`, `PERF_VEHICLE_SPEED`(FAQ 권고) 사용.

### 4.3 도메인 ↔ 퍼미션 전체 표 (AAR `@RequiresPermission` 기준)

| 도메인 | 조회 | 제어 |
| --- | --- | --- |
| Door | CAR_DOORS | CONTROL_CAR_DOORS |
| Window, Wiper | CAR_WINDOWS | CONTROL_CAR_WINDOWS |
| HVAC, SteeringWheel 열선, SideMirror 열선 리스너 | CAR_CLIMATE | CONTROL_CAR_CLIMATE |
| DrivingMode, Brake, Odometer, SteeringWheel 각도, Gear | CAR_DRIVE | CONTROL_CAR_DRIVE |
| EvBattery, Fuel | CAR_ENERGY | CONTROL_CAR_ENERGY |
| CarInfo, Display, (EvBattery.getEvBatteryCapacity) | CAR_INFO | CONTROL_CAR_INFO |
| Light, TurnSignal | CAR_LIGHTS | CONTROL_CAR_LIGHTS |
| SideMirror 열선 상태 | CAR_MIRRORS | CONTROL_CAR_MIRRORS |
| Safety, Tire | CAR_SAFETY (일부 PRIVATE_CAR_SAFETY) | CONTROL_CAR_SAFETY |
| Seat | CAR_SEATS | CONTROL_CAR_SEATS |
| Toll(ETCS) | CAR_TOLL | CONTROL_CAR_TOLL |

매니페스트: `<uses-permission android:name="pleos.car.permission.CAR_ENERGY" />` 형식. `PRIVATE_*`는 비공개.

### 4.4 오류 처리
- `onFailed(e: Exception)` — `e`가 `VehicleException`이면 `errorCode: ErrorCode` 확인. ErrorCode(enum, `vehicle-data` 아티팩트의 `ai.pleos.playground.vehicle.constant.ErrorCode`):
  `ERROR_NOT_SUPPORTED_PROPERTY`(UI 비활성) · `ERROR_NOT_SUPPORTED_AREA` · `ERROR_NOT_SUPPORTED_VALUE_TYPE` · `ERROR_SECURITY_PERMISSION`(매니페스트 확인) · `ERROR_SERVICE_CONNECTION_FAILURE`(initialize/에뮬레이터 확인·재시도) · `ERROR_INTERNAL_SERVICE`(재시도) · `ERROR_DRIVING_RESTRICTED`(주행 중 제한 안내) · `ERROR_EMPTY_RESULT` · `ERROR_UNDEFINED`.
- **주의(실측)**: Vehicle 2.3.2 AAR에서 `VehicleException` 클래스가 R8 난독화되어 `ai.pleos.playground.vehicle.callback.exception.a` 로 들어 있음 → `e is VehicleException` 컴파일 불가. 이 저장소는 `PleosErrors.codeOf(e)`(리플렉션 + 메시지 "Error code: X" 파싱)로 우회. 향후 SDK 버전에서 수정될 수 있음.
- `initialize()`는 연결 콜백이 없으므로 저장소는 `checkEvBatteryStateOfChargeCapabilities`로 최대 8회(1초 간격) 프로브해 연결을 판정.

### 4.5 2.3.2 릴리즈 노트 핵심
- Breaking: `CruiseControl` 상수 교체, `checkHvacFanSpeedCapabilities` 콜백 타입 `Map<SeatArea, AreaBounds<Int>>`, `FanDirectionType` 8종으로 확장, Area 클래스 equals 추가.
- New: `checkVINInfoCapabilities/getVINInfo`, `getCurrentEvEfficiency`, `registerEvBatteryStateOfCharge`, `CapabilityConfig/TripStatistics/TelltaleData/Telltales` 모델, Smart Glass, ETCS 볼륨.
- Deprecated: `getCRNInfo`, `checkWheelAreaCapabilities`. Shutdown: `getHvacTemperatureValueSuggestion`, `VehicleSpeedListener`.
- 문서 숨김: `EvBatteryLevelListener`, `EvChargePortOpenListener`, `RangeRemainingListener`.

### 4.6 Pleos Connect Emulator v2.1.0 실측 (2026-10-29, EV Companion 앱)
- 연결: `Vehicle(context).initialize()` 후 약 1.6초 뒤 `ServiceConnected`, 첫 `checkEvBatteryStateOfChargeCapabilities` 가 `accessType=READ` 로 성공(재시도 불필요). 바인딩 대상 `ai.pleos.playground.service.vehicle/.VehicleService`(action `ai.pleos.playground.vehicle.VEHICLE_START`). SDK 내부 로그 태그 `[Pleos][SDK]Vehicle`, `[Pleos][SDK]a/b`(난독화)에서 `getCapabilityConfig`, `getProperty_onResult`, `onChangeEvent` 를 볼 수 있다.
- 값이 오는 것: `EV_BATTERY_SOC`(100), `EV_CHARGE_SWITCH`(false), `PERF_ODOMETER`(0.0, 값이 같아도 3~4초마다 `onChangeEvent` 반복), `INFO_VIN`("invalid").
- 에뮬레이터에서 미지원: `TRIP_EV_EFFICIENCY`(getCurrentEvEfficiency/register → `ERROR_NOT_SUPPORTED_PROPERTY`), `SINCE_CHARGE_EV_EFFICIENCY`(0x21600b14 → `IllegalArgumentException: property ID is not in carPropertyConfig list`), `TRIP_DRIVING_DISTANCE`/`TRIP_DRIVING_TIME`(`ERROR_NOT_SUPPORTED_PROPERTY`), `DRIVING_MODE`(0x21400410 → IllegalArgumentException). 실차에서는 다를 수 있으므로 앱은 모두 nullable 로 두고 실패는 로그만 남긴다.
- AAOS CarPropertyManager: `android.car.permission.CAR_ENERGY` 런타임 허용 전에는 `getProperty` 가 `SecurityException`, `registerCallback` 이 `propertyId is not in carPropertyConfig list`(권한 없는 속성은 config 목록에서 빠짐). 허용 후 앱을 재시작하면 `RANGE_REMAINING`(500 km), `EV_BATTERY_LEVEL`/`INFO_EV_BATTERY_CAPACITY`(77.4 kWh), `EV_CHARGE_STATE`(FULLY_CHARGED), `EV_CHARGE_PORT_OPEN/CONNECTED`(false) 수신. `CAR_INFO`·`CAR_ENERGY_PORTS` 는 normal 권한이라 허용 전에도 동작.
- Pleos 퍼미션 3종(`pleos.car.permission.CAR_ENERGY/CAR_DRIVE/CAR_INFO`)은 설치 시 자동 granted(`adb shell dumpsys package com.example.pleosev` 로 확인). `CAR_INFO` 는 2026-11-03 VIN 표시를 빼면서 선언에서 제거.

---

## 5. FusedLocation SDK (v1.1.3) — `pleos.car.permission.FUSED_LOCATION` (dangerous)
```kotlin
val fused = FusedLocation(context)
if (fused.isSupported()) fused.initialize() else { /* 위치 기능 숨김 */ }
fused.getLastKnownLocation { loc: LocationData? -> }            // 마지막 위치 1회 (null 가능)
val l = object : FusedLocationListener {
    override fun onChanged(location: LocationData?) { location ?: return }  // lat, lon, altitude, heading, confidence, timestampMillis
    override fun onError(exception: Exception) {}
}
fused.registerFusedLocationCallback(l); fused.unregisterFusedLocationCallback(l)
fused.addProximityAlert(ProximityAlertRequest(latitude, longitude, radiusMeters, transitionTypes), ProximityAlertListener{onRegistered/onTransition(ProximityAlertEvent(alertId, transition ENTER/EXIT))/onError}); fused.removeProximityAlert(...)
fused.showLocationAccessBlockedPopup()   // 차량 개인정보 설정으로 차단 시 안내 (허용 API 아님)
fused.release()                          // unregister 후 반드시 호출, 이후 재사용 금지
```
- 차량 모델에 따라 미지원 → 반드시 `isSupported()`. Android `ACCESS_FINE_LOCATION` 정책과 별개. Playground에서 "준비 중" 상태(신청 불가).
- EV 활용: 충전소 근접 알림(ProximityAlert), 현재 위치 기반 충전소 탐색.

---

## 6. NaviHelper SDK (v3.0.5) — 내비 탑재 차량 전용, 신청 없이 사용 가능
- 퍼미션: `NAVI_ROUTE`(get*Info, requestTrip) / `NAVI_ROUTE_SEARCH`(requestRoute, requestRouteWithWaypoints, cancelRoute, requestReRoute, add/removeWaypoint, changeRouteOption) / `NAVI_CUSTOM_MAP`(requestNavigationScreen).
```kotlin
val navi = NaviHelper(applicationContext)
navi.initialize(onConnected = { navi.addListener(listener) }, onFailed = { t -> })
navi.requestRoute(RouteInfo(latitude, longitude, displayLongitude=null, displayLatitude=null, poiName, poiId, poiSubId, address /*, routeOption=RECOMMENDED*/))
navi.requestRouteWithWaypoints(RouteWithWaypointsRequest(destination = RouteInfo(...), waypoints = listOf(RequestWaypointInfo(latitude, longitude, poiName, waypointIndex = WaypointIndex.FIRST, poiId, address, poiSubId))))
navi.addWaypoint(RequestWaypointInfo(...)); navi.removeWaypoint(WaypointIndex.FIRST /* SECOND, ALL */)   // 경유지 최대 2개
navi.changeRouteOption(RouteOption.HIGHWAY)  // RECOMMENDED, SHORT_DISTANCE, SHORT_TIME, FREE, HIGHWAY, COMFORTABLE, SCHOOL_ZONE
navi.requestReRoute(); navi.cancelRoute()
navi.getDestinationInfo(); navi.getWaypointInfo(); navi.getCurrentLocationInfo(); navi.getRouteStateInfo(); navi.getTBTInfo(); navi.getBookmarkInfo(); navi.getRecentDestinationInfo(); navi.getChargerOperatorInfo()
navi.requestTrip(TripRequest); navi.requestNavigationScreen(NavigationScreenRequest)
navi.removeListener(listener); navi.release()
```
- 결과는 전부 `NaviHelperEventListener` 콜백: 경로(`onRouteStarted(RouteStartInfo)`, `onRoutingCompleted()`, `onRouteCancelled`, `onReRouteEnded`, `onRouteOptionChanged`), 목적지/경유지(`onDestinationChanged/Arrived`, `onWaypointChanged`, `onWayPointArrived`, `onWayPointInfo(List<WaypointInfo>)`), 정보(`onDestinationInfo(DestinationInfo: remainDistance/remainTime)`, `onCurrentLocationInfo`, `onRouteStateInfo`, `onTBTInfo`, **`onChargerOperatorInfo`**, `onTripInfo`, `onNavigationStatus(NavigationStatus)`, `onDrivingInfo`, `onCurrentRoadSpeedLimit`, `onCameraAlert`, `onCautionPointInfo`), `onError(NaviErrorCode: CAUSE_LOW_BATTERY, CAUSE_SEARCH_ROUTE_OVER_DISTANCE, CAUSE_SEARCH_ROUTE_TOO_SHORT_DISTANCE, CAUSE_SEARCH_ROUTE)`.
- 3.0.5 Breaking: 데이터 생성자 좌표 순서 `(latitude, longitude)`, `onNavigationStatus` 타입 변경, `totalDistance/totalTime → remainDistance/remainTime`. `onRouteEnded` → `onRoutingCompleted`.
- EV 활용: 충전소를 목적지/경유지로 설정, `ChargerOperator`·`ChargingPlan` 데이터 모델(`ai.pleos.playground.navi.data`) 활용, 남은 거리 vs 주행가능거리 비교.

### 6.1 Pleos Connect Emulator v2.1.0 실측 (2026-11-02, 상세: `docs/SPIKE_NAVI_EV.md`)
- 에뮬레이터에 Navi Service(`ai.pleos.playground.service.navi`, action `ai.pleos.playground.intent.action.NAVI_START`)와 내비 앱(`ai.umos.maps.android.navigation.app`)이 있어 NaviHelper가 동작한다. `initialize` 50~280 ms, 콜백은 binder 스레드.
- `remainDestinationChargingValue`/`remain*ChargingInt` 는 **Wh**(남은 에너지), `TripInfo.estimatedSocAtDest` 는 %, `energyConsumption` 은 Wh. `DrivingState.targetSoc` = 10.
- 이 남은 에너지 값은 경로를 계획할 때의 배터리로 계산되고, 배터리가 바뀌어도 따라가지 않는다(2026-11-04: SOC 25 → 100 %로 바꾸고 2분 넘게 지나도 6.4 kWh). 새 경로 요청(`requestRoute`) 때 다시 계산된다(→ 22.7 kWh). `requestTrip` 은 늘 현재 배터리로 계산한다(SOC 100 % → 충전 경유지 없음, 7 % → 가까운 충전소 경유).
- 배터리가 부족하면 내비가 충전소를 **자동 경유지**로 넣는다(`DrivingInfo.waypoints` 의 `isChargingStation=true`, `onWayPointInfo` 에는 없음, 3rd party가 지울 수 없음). 좌표·`poiId` 는 `requestTrip(목적지)` 의 `routePoints`(AUTO, EV_CHARGING)로만 얻는다.
- `poiId=""` 로 경로·경유지 요청 가능. 단 충전소를 좌표만으로 넣으면 일반 경유지로 취급돼 내비가 충전 경유지를 따로 또 넣는다. 내비 `poiId` 와 함께 넣어야 충전소로 인식한다. statId → `poiId` 변환 방법은 없음.
- `WaypointInfo` 좌표는 0.0 으로 온다. 정차 중 스스로 오는 콜백은 `onCurrentRoadSpeedLimit`(약 1 s)뿐. `adb emu geo fix` 로는 내비 위치가 바뀌지 않는다.
- 앱이 뒤로 가면 1~2분 안에 동결되어 콜백이 끊긴다(`specialUse` FGS로 유지됨). 헤드업 알림과 액션 버튼은 내비 위에 표시되고, 액션 → `addWaypoint` 가 앱 전환 없이 동작한다. 알림 권한은 `pm grant --user current` 로 준다(AAOS 사용자 10).

---

## 7. TextToSpeech / SpeechToText (v3.3.1) — 서버 모드는 Playground 승인·클라이언트 ID 필요
- 공통: 모드 `HYBRID`(기본, 서버 우선→실패 시 온디바이스) / `SERVER` / `ON_DEVICE`(앱 등록 불필요). 퍼미션 `pleos.car.permission.TTS_SERVICE` / `STT_SERVICE`. 지원 로케일 `ko-KR, en-US, fr-CA, es-US, es-MX`. `setLocale(Locale)`, 미호출 시 이전 설정값→시스템 로케일.
- TTS: `TextToSpeech(ctx, Mode.HYBRID)` → `initialize(OnInitializeListener)` → `addEventListener(EventListener{onReady,onStart,onUpdatedRms,onDone,onStop,onError(ErrorType)})` → (서버 모드) `registerApp(clientID, clientSecret, OnServerConnectionListener)` → `onConnected()` 후 `speak(text, isFinal=true)` / `stop()` → `removeEventListener` → `release()`. 음성 안내 앞 150~200 ms 선행 묵음 권장(초성 손실 방지).
- STT: `SpeechToText(ctx, Mode)` → `initialize` → `addListener(ResultListener{onReady,onStartedRecognition,onUpdated(stt, completed),onUpdatedEpdData,onUpdatedRms,onEndedRecognition,onError})` → `request()`(마이크) 또는 `sendAudio(ByteArray)`+`completeAudioSend()`(ON_DEVICE 전용, 16 kHz 16-bit mono PCM 3,200 B/100 ms) → `stop()` → `removeListener` → `release()`. 음성 "명령" 실행은 Gleo Interface(App Integration Guide) 영역.
- ErrorType: `AUDIO_ERROR, INVALID_REQUEST_ERROR, MIC_ERROR, NETWORK_ERROR, NOT_SUPPORTED_API, ON_DEVICE_ENGINE_ERROR, PERMISSION_DENIED, SERVER_INTERNAL_ERROR, TIMEOUT, UNAUTHENTICATED, UNAVAILABLE_LANGUAGE, UNKNOWN` (when에 else 분기 필수).
- EV 활용: 저배터리/충전 완료 음성 알림(TTS, ON_DEVICE면 승인 불필요).

---

## 8. AAOS 표준 API 병행 (SDK 정책상 권장)

| 데이터 | AAOS | 퍼미션 | 단위 |
| --- | --- | --- | --- |
| 주행 가능 거리 | `VehiclePropertyIds.RANGE_REMAINING` | `android.car.permission.CAR_ENERGY` (**dangerous**, 런타임 요청) | m |
| 배터리 에너지/용량 | `EV_BATTERY_LEVEL` / `INFO_EV_BATTERY_CAPACITY` | CAR_ENERGY / `CAR_INFO`(normal) | Wh |
| 충전 포트 | `EV_CHARGE_PORT_OPEN`, `EV_CHARGE_PORT_CONNECTED` | `CAR_ENERGY_PORTS`(normal) | bool |
| 충전 전력/상태 | `EV_BATTERY_INSTANTANEOUS_CHARGE_RATE`, `EV_CHARGE_STATE`(API 33) | CAR_ENERGY | mW / enum |
| 속도(클러스터 동일) | `PERF_VEHICLE_SPEED` | `CAR_SPEED`(dangerous) | m/s |
| 기어/시동 | `GEAR_SELECTION`, `IGNITION_STATE` | `CAR_POWERTRAIN` | |
| 주행 규제 | `CarUxRestrictionsManager.registerListener { r -> r.isRequiresDistractionOptimization; r.activeRestrictions }` | — | `UX_RESTRICTIONS_NO_VIDEO(16)`, `NO_KEYBOARD(8)`, `NO_SETUP(64)`, `LIMIT_CONTENT(32)` … |

- `Car.createCar(context)` → `getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager` → `getProperty<Any>(propId, 0)`, `registerCallback(cb, propId, SENSOR_RATE_ONCHANGE)`. 모델별 지원 여부는 `CarPropertyManager`로 실제 확인(모델명 분기 금지). SDK가 제공하지 않는 특수 프로퍼티는 기술지원 검토 필요.
- 이 저장소의 `car/CarPropertySource.kt`, `car/UxRestrictionsMonitor.kt` 참고.

---

## 9. 반드시 지킬 정책 (자세한 내용은 `PLEOS_APP_POLICY_SUMMARY.md`)
- **DO(distractionOptimized) 미선언 앱은 주행 중 사용 불가.** 선언해도 `CarUxRestrictions`에 따라 기능 축소 필수. 북미: 주행 중 키보드·검색(음성 한정)·설정·동영상 등 전면 제한, 유럽/기타: 일부 허용.
- 성능: 주행 중 CPU 5 % 미만(내비 앱 15 %), 정차 25 % 이하, **RAM 400 MB 이하**, 백그라운드 폴링 최소화, 콜드 스타트 스플래시 최소화. 심사 기준 앱 실행 10 초, 터치 반응 0.25 초, 버튼 응답 2 초.
- 화면: Driving view + 앱 영역(최대 2개 window). `screenOrientation` 미지정=portrait(1 window), landscape 지정=2 window. 두 방향 모두 설계, 백그라운드 전환 시 상태 저장·불필요 작업 중단.
- 카테고리: EV 대시보드/충전 앱은 **Mobility Services**(차량 관리, 충전소 찾기·경유지 설정이 주행 중 제한적 허용 예시) 가 적합.
- 하드웨어 직접 접근 금지, 모바일 전용 API(Telephony/SMS 등) 제한 고려, 네트워크 불안정 대비(캐싱·재시도·오프라인 UI).
- 보안: 릴리즈는 정식 서명키(minSdk 28 — 키 로테이션), 하드코딩 키 금지(Keystore), TLS 1.2+, 위치 데이터 암호화·최소 보관, 위치 사용 시 위치정보 이용약관 URL 필수.
- 오디오 사용 시 오디오 포커스 정책(Interaction Matrix) 및 Suspend(IGN OFF) 처리.
- 결제: Google Play Billing 불가, 플랫폼 Payment SDK(준비 중).

---

## 10. 이 저장소(EV Companion) 구조
```
app/src/main/java/com/example/pleosev/
├─ MainActivity.kt                 Compose 진입, android.car.permission.CAR_ENERGY 런타임 요청
├─ model/EvDashboardState.kt       화면 상태(모든 차량 값 nullable)
├─ data/VehicleRepository.kt       데이터 소스 인터페이스
├─ data/PleosVehicleRepository.kt  Pleos Vehicle SDK(EvBattery/Odometer/DrivingMode) + CarPropertySource 병합
├─ data/FakeVehicleRepository.kt   Pleos 에뮬레이터 없을 때(디버그) 데모 데이터
├─ data/PleosErrors.kt             VehicleException 난독화 우회 오류코드 추출
├─ car/CarPropertySource.kt        AAOS CarPropertyManager(주행가능거리/배터리 Wh/충전 포트·상태)
├─ car/UxRestrictionsMonitor.kt    CarUxRestrictions(주행 중 간소화)
└─ ui/DashboardViewModel.kt, ui/DashboardScreen.kt, ui/theme/Theme.kt
```
- 매니페스트 선언 퍼미션: `pleos.car.permission.CAR_ENERGY / CAR_DRIVE`, `android.car.permission.CAR_ENERGY / CAR_ENERGY_PORTS / CAR_INFO`, `uses-library android.car`, `distractionOptimized=true`. 충전 도우미용 NAVI·알림·포그라운드 서비스 권한까지 포함한 전체 목록은 README "선언된 퍼미션".
- 에뮬레이터 검증 스크린샷(2026-10-29): `docs/screenshots/pleos-emulator-dashboard.png`(연결 직후, SOC 100 %·500 km), `docs/screenshots/pleos-emulator-soc42.png`(Virtual Console 로 SOC 42 %·350 km 설정 후).
- 다음 기능 후보: NaviHelper 충전소 경유지 설정, FusedLocation 근접 알림, TTS 저배터리 안내, 충전 제한 설정(Vehicle Control 승인 후), 트립 통계 화면(`TripStatistics` 4종), 텔테일 경고 표시(`CAR_SAFETY`).

---

## 11. 링크
- 시작하기: https://document.pleos.ai/docs/pleos-only/getting-started
- 에뮬레이터 설치: https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator
- Virtual Console: https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide
- Vehicle SDK 가이드: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro
- Vehicle 샘플 앱 가이드: https://document.pleos.ai/docs/pleos-only/tutorial/vehicle-sdk-sample-app
- 릴리즈 노트: https://document.pleos.ai/docs/pleos-only/release-note/sdk-261015
- Playground(계정/프로젝트/SDK 신청): https://pleos.ai/playground
- App Market Console(심사 제출): https://appmarket.pleos.ai/console/
