<#
.SYNOPSIS
  Pleos Connect Virtual Console 의 HTTP API 로 에뮬레이터 차량 상태를 조회/설정합니다 (Pleos Connect Emulator v2.1.0).

.DESCRIPTION
  Virtual Console 웹 UI(http://127.0.0.1:8088/)가 내부적으로 호출하는 API 를 그대로 씁니다 (2026-10-29 확인).
  실행 전 포트 포워딩이 필요하며 스크립트가 `adb forward tcp:8088 tcp:8088` 을 자동으로 실행합니다.
  쓰기 API 는 브라우저 세션 하트비트 `POST /browser/session?client=<id>` (타임아웃 3초) 가 살아 있을 때만 받습니다
  (없으면 503 {"error":"emulator_inactive"}). 웹 UI 는 1초마다 보내고, 이 스크립트는 쓰기 직전에 직접 보냅니다.
    GET /vehicle/catalog   카드(feature) 목록: write_endpoint, control.field / params, 매핑된 car_property
    GET /vehicle/state     현재 차량 상태 JSON (ev_battery, odometer, gear, controls, body, hvac ...)
    PUT <write_endpoint>?<params>&<field>=<value>   값 쓰기 (웹 UI 와 동일. POST 도 동작)
  Energy 카드 매핑:
    Battery SOC       PUT /vehicle/ev-battery?field=soc&value=42            -> EV_BATTERY_SOC (vendor 0x21400BA7)
    Battery Energy    PUT /vehicle/ev-battery?field=battery_level&value=40  -> EV_BATTERY_LEVEL
    Battery Capacity  PUT /vehicle/ev-battery?field=capacity&value=77.4     -> INFO_EV_BATTERY_CAPACITY
    Charge Switch     PUT /vehicle/ev-battery?field=charge_switch&value=true
    Charge Port       PUT /vehicle/ev-battery?field=charge_port_open&value=true
    Charge Cur. Limit PUT /vehicle/ev-battery?field=current_limit&value=32  -> EV_CHARGE_CURRENT_DRAW_LIMIT (10~48 A)
    Charge % Limit    PUT /vehicle/ev-battery?field=percent_limit&value=80  -> EV_CHARGE_PERCENT_LIMIT (50~100, 5 단위)
    Remaining Range   PUT /vehicle/odometer?remaining_range_m=350000        -> RANGE_REMAINING
    Odometer          PUT /vehicle/odometer?traveled_distance_km=12345      -> PERF_ODOMETER
    Trip time         PUT /vehicle/odometer?usage_time_min=90               -> TRIP_DRIVING_TIME (콘솔에는 있지만 SDK 는 미지원 응답)
    Gear              PUT /vehicle/gear?gear=Drive  (Park / Reverse / Neutral / Drive)
  주행 입력 (2026-11-03 확인, 드라이브 코치 검증용):
    Speed             PUT /vehicle/controls?speed=30        -> PERF_VEHICLE_SPEED (정수 km/h 만, 0~240. 웹 UI 에는 카드가 없음)
    Brake pedal       PUT /vehicle/controls?brake=100       -> 브레이크 눌림 (0~100 %, SDK 는 on/off 만 봄)
    Steering          PUT /vehicle/controls?steering=-470   -> PERF_STEERING_ANGLE (핸들각, + 가 왼쪽: SDK 에는 부호가 바뀌어 -로 들어옴)
  전체 63 개 카드(body, hvac, lights, seat, telltales ...)는 -Catalog 로 확인합니다.

.EXAMPLE
  pwsh -File scripts/pleos-vc.ps1 -Soc 42 -RangeKm 350
  pwsh -File scripts/pleos-vc.ps1 -ChargePort open -ChargeSwitch on
  pwsh -File scripts/pleos-vc.ps1 -Gear D
  pwsh -File scripts/pleos-vc.ps1 -Gear D -SpeedKph 30 -BrakePct 0     # 주행 상태 (드라이브 코치 트립 기록용)
  pwsh -File scripts/pleos-vc.ps1 -SteeringDeg -470                    # 핸들 오른쪽 거의 끝까지
  pwsh -File scripts/pleos-vc.ps1 -Soc 20 -Hold 30            # 쓰고 나서 30초 동안 활성 유지 (웹 UI 없이 앱 반응을 볼 때)
  pwsh -File scripts/pleos-vc.ps1 -State              # 현재 상태 요약 (-Raw 를 붙이면 전체 JSON)
  pwsh -File scripts/pleos-vc.ps1 -Catalog            # 카드 id / endpoint / field / car property 목록
  pwsh -File scripts/pleos-vc.ps1 -OdometerKm 12345
  pwsh -File scripts/pleos-vc.ps1 -Put "/vehicle/odometer?usage_time_min=90"   # 임의 endpoint 를 PUT
#>
param(
    [int]$Soc = -1,                 # 0~100 %
    [double]$BatteryKwh = -1,       # 0~200 kWh (현재 에너지)
    [double]$CapacityKwh = -1,      # 0~200 kWh (최대 용량)
    [double]$RangeKm = -1,          # 0~1000 km (주행 가능 거리)
    [double]$OdometerKm = -1,       # 0~1000000 km (누적 주행거리)
    [ValidateSet("", "on", "off")][string]$ChargeSwitch = "",
    [ValidateSet("", "open", "closed")][string]$ChargePort = "",
    [ValidateSet("", "P", "R", "N", "D")][string]$Gear = "",
    [int]$SpeedKph = -1,            # 0~240 km/h (정수만 받음)
    [int]$BrakePct = -1,            # 0~100 %
    [int]$SteeringDeg = 9999,       # -540~540 핸들각 (+ 가 왼쪽)
    [string[]]$Put = @(),           # 임의 "<endpoint>?<query>" (PUT)
    [int]$Hold = 0,                 # 쓰기 후 N초 동안 하트비트를 유지해 콘솔을 활성 상태로 둠
    [switch]$State,
    [switch]$Catalog,
    [switch]$Raw,
    [string]$BaseUrl = "http://127.0.0.1:8088",
    [string]$Adb = ""
)
$ErrorActionPreference = "Stop"

if (-not $Adb) {
    $Adb = Join-Path $env:LOCALAPPDATA "Android/Sdk/platform-tools/adb.exe"
    if (-not (Test-Path -LiteralPath $Adb)) { $Adb = "adb" }
}
& $Adb forward tcp:8088 tcp:8088 | Out-Null

function Invoke-Vc([string]$Method, [string]$PathAndQuery) {
    $url = $BaseUrl + $PathAndQuery
    try {
        $r = Invoke-WebRequest -Uri $url -Method $Method -UseBasicParsing -TimeoutSec 10
        return $r.Content
    } catch {
        throw "$Method $url 실패: $($_.Exception.Message)  (에뮬레이터가 실행 중인지, adb forward tcp:8088 이 되어 있는지 확인)"
    }
}

$writes = @()
if ($Soc -ge 0)         { $writes += "/vehicle/ev-battery?field=soc&value=$Soc" }
if ($BatteryKwh -ge 0)  { $writes += "/vehicle/ev-battery?field=battery_level&value=$BatteryKwh" }
if ($CapacityKwh -ge 0) { $writes += "/vehicle/ev-battery?field=capacity&value=$CapacityKwh" }
if ($RangeKm -ge 0)     { $writes += "/vehicle/odometer?remaining_range_m=$([int][math]::Round($RangeKm * 1000))" }
if ($OdometerKm -ge 0)  { $writes += "/vehicle/odometer?traveled_distance_km=$OdometerKm" }
if ($ChargeSwitch)      { $writes += "/vehicle/ev-battery?field=charge_switch&value=$(($ChargeSwitch -eq 'on').ToString().ToLower())" }
if ($ChargePort)        { $writes += "/vehicle/ev-battery?field=charge_port_open&value=$(($ChargePort -eq 'open').ToString().ToLower())" }
if ($Gear)              { $g = @{ P = "Park"; R = "Reverse"; N = "Neutral"; D = "Drive" }[$Gear]; $writes += "/vehicle/gear?gear=$g" }
if ($SpeedKph -ge 0)    { $writes += "/vehicle/controls?speed=$SpeedKph" }
if ($BrakePct -ge 0)    { $writes += "/vehicle/controls?brake=$BrakePct" }
if ($SteeringDeg -ne 9999) { $writes += "/vehicle/controls?steering=$SteeringDeg" }
$writes += $Put

function Send-Heartbeat {
    # 서버는 마지막 하트비트 후 3초(timeout_ms) 동안만 emulator_active=true 로 두고 쓰기를 받습니다.
    Invoke-Vc "POST" "/browser/session?client=pleos-vc-ps1" | Out-Null
}

foreach ($w in $writes) {
    Send-Heartbeat
    $resp = Invoke-Vc "PUT" $w
    Write-Host "PUT $w"
    if ($Raw) { Write-Host $resp }
}
if ($Hold -gt 0) {
    Write-Host "하트비트 유지 $Hold 초 (Ctrl+C 로 중단)"
    for ($i = 0; $i -lt $Hold; $i++) { Send-Heartbeat; Start-Sleep -Seconds 1 }
}

if ($Catalog) {
    $cat = (Invoke-Vc "GET" "/vehicle/catalog") | ConvertFrom-Json
    if ($Raw) { $cat | ConvertTo-Json -Depth 10; return }
    $cat.features | ForEach-Object {
        $c = $_.control
        $params = if ($c.params) { ($c.params.PSObject.Properties | ForEach-Object { "$($_.Name)=$($_.Value)&" }) -join "" } else { "" }
        $range = if ($c.kind -eq "range") { "[$($c.min)..$($c.max)]" } elseif ($c.options) { "{" + (($c.options | ForEach-Object { $_.value }) -join "|") + "}" } else { "" }
        "{0,-11} {1,-34} {2}?{3}{4}=<v>{5}  -> {6}" -f $_.domain, $_.id, $_.write_endpoint, $params, $c.field, $range, $_.car_property.name
    }
    return
}

if ($State -or $writes.Count -eq 0) {
    $st = (Invoke-Vc "GET" "/vehicle/state") | ConvertFrom-Json
    if ($Raw) { $st | ConvertTo-Json -Depth 10; return }
    $ev = $st.ev_battery; $od = $st.odometer
    "ev_battery : SOC {0} %, energy {1} / {2} kWh, charge_switch={3}, port_open={4}, charge_limit {5} %, current_limit {6} A" -f $ev.state_of_charge, $ev.current_energy_kwh, $ev.maximum_capacity_kwh, $ev.charging_activated, $ev.charging_door_opened, $ev.charge_limit_pct, $ev.maximum_current_a
    "odometer   : range {0} km, traveled {1} km, usage {2} min" -f ($od.remaining_range_m / 1000), $od.traveled_distance_km, $od.usage_time_min
    "gear       : {0}   speed {1} km/h   brake {2} %   steering {3}°" -f $st.gear.actual.value, $st.controls.speed_kph, $st.controls.brake_pct, $st.controls.steering_deg
}
