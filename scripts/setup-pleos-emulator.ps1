<#
.SYNOPSIS
  Pleos Connect Emulator (v2.1.0, Android 14 / API 34) 설치 및 AVD 생성 스크립트 (Windows, PowerShell 7).

.DESCRIPTION
  1) 고전 sdkmanager (cmdline-tools 19.0) 를 <SDK>\cmdline-tools\19.0 에 설치
     - cmdline-tools 22.0 이상의 sdkmanager.bat 는 새 Android CLI(`android sdk`) 로 넘기는 래퍼라서
       ~/.android/repositories.cfg 의 사용자 업데이트 사이트를 읽지 않습니다 (2026-10-28 확인).
       그래서 19.0 을 latest 옆에 나란히 두고 Pleos 이미지 설치에만 사용합니다.
  2) Pleos Connect system image 업데이트 사이트를 ~/.android/repositories.cfg 에 등록
     - 키 형식: count / srcNN / dispNN / enabledNN (Android Studio > SDK Manager > SDK Update Sites 와 같은 파일)
  3) sdkmanager 로 Pleos system image (약 4.1 GB) 설치
     - 42dot "Software Development Kit License Agreement" 프롬프트가 뜨면 내용을 읽고 y 를 입력하세요.
     - -AcceptPleosLicense 스위치를 주면 프롬프트에 자동으로 y 를 보냅니다. 라이선스에 동의한 경우에만 쓰세요.
  4) Automotive AVD (기본 이름 Pleos_Connect_v2_1) 생성: 디바이스 프로필 "Pleos Connect v2.1.0" (2560x1440, 이미지 동봉),
     Cold boot, RAM 4 GB, GPU host, data 8 GB

  주의: 일반 PowerShell 창에서 실행하세요. MSIX 앱 패키지 ID를 상속한 프로세스(예: Claude Code 의 Bash 도구)에서
  실행하면 %LOCALAPPDATA% 아래 쓰기가 패키지 전용 폴더로 리다이렉트되어 Android Studio 가 결과를 보지 못합니다.

  Android Studio GUI 로 하려면 docs/PLEOS_SDK_GUIDE.md 의 "에뮬레이터 설치" 절을 참고하세요.

.PARAMETER SdkRoot
  Android SDK 경로 (기본: %LOCALAPPDATA%\Android\Sdk)
.PARAMETER AvdName
  생성할 AVD 이름 (기본: Pleos_Connect_v2_1)
.PARAMETER JavaHome
  sdkmanager 가 쓸 JDK (기본: $env:JAVA_HOME, 없으면 D:\Android Studio\jbr)
.PARAMETER AcceptPleosLicense
  42dot SDK 라이선스 프롬프트에 자동으로 y 를 보냅니다.
.PARAMETER SkipAvd
  system image 설치까지만 하고 AVD 는 만들지 않습니다.
#>
param(
    [string]$SdkRoot = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$AvdName = "Pleos_Connect_v2_1",
    [string]$JavaHome = "",
    [switch]$AcceptPleosLicense,
    [switch]$SkipAvd
)

$ErrorActionPreference = "Stop"
$SysImgPackage   = "system-images;android-34;pleos_car_emulator;x86_64"
$UpdateSiteUrl   = "https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml"
$UpdateSiteName  = "Pleos Connect System Image"
$ClassicToolsUrl = "https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip"  # cmdline-tools 19.0 (고전 sdkmanager)
$ClassicToolsDir = Join-Path $SdkRoot "cmdline-tools\19.0"

Write-Host "== Android SDK: $SdkRoot"
if (-not (Test-Path -LiteralPath $SdkRoot)) { throw "SDK 경로가 없습니다: $SdkRoot" }

# JDK
if (-not $JavaHome) {
    if ($env:JAVA_HOME) { $JavaHome = $env:JAVA_HOME }
    elseif (Test-Path -LiteralPath "D:\Android Studio\jbr") { $JavaHome = "D:\Android Studio\jbr" }
}
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome "bin\java.exe"))) {
    throw "JDK 를 찾을 수 없습니다. -JavaHome 으로 지정하세요 (예: 'D:\Android Studio\jbr')."
}
$env:JAVA_HOME = $JavaHome
$env:Path = "$JavaHome\bin;$env:Path"
Write-Host "== JAVA_HOME: $JavaHome"

# 1) 고전 sdkmanager (cmdline-tools 19.0)
$sdkmanager = Join-Path $ClassicToolsDir "bin\sdkmanager.bat"
if (-not (Test-Path -LiteralPath $sdkmanager)) {
    Write-Host "== cmdline-tools 19.0 설치 중 ($ClassicToolsUrl)"
    $zip = Join-Path $env:TEMP "commandlinetools-19.zip"
    Invoke-WebRequest -Uri $ClassicToolsUrl -OutFile $zip
    $tmp = Join-Path $env:TEMP "cmdline-tools-19-extract"
    if (Test-Path -LiteralPath $tmp) { Remove-Item -LiteralPath $tmp -Recurse -Force }
    Expand-Archive -LiteralPath $zip -DestinationPath $tmp
    New-Item -ItemType Directory -Force -Path (Split-Path $ClassicToolsDir) | Out-Null
    Move-Item -LiteralPath (Join-Path $tmp "cmdline-tools") -Destination $ClassicToolsDir
    Remove-Item -LiteralPath $zip -Force
}
if (-not (Test-Path -LiteralPath $sdkmanager)) { throw "sdkmanager 를 찾을 수 없습니다: $sdkmanager" }
if (Select-String -LiteralPath $sdkmanager -SimpleMatch "Android CLI" -Quiet) {
    throw "$sdkmanager 는 Android CLI 래퍼입니다. 고전 sdkmanager(cmdline-tools 19.0 이하)가 필요합니다."
}

# avdmanager 는 latest(23.0) 의 것도 아직 고전 도구이므로 있으면 그것을, 없으면 19.0 의 것을 사용
$avdmanager = Join-Path $SdkRoot "cmdline-tools\latest\bin\avdmanager.bat"
if (-not (Test-Path -LiteralPath $avdmanager)) { $avdmanager = Join-Path $ClassicToolsDir "bin\avdmanager.bat" }

# 2) Pleos update site 등록 (~/.android/repositories.cfg)
$cfg = Join-Path $env:USERPROFILE ".android\repositories.cfg"
$lines = @()
if (Test-Path -LiteralPath $cfg) { $lines = @(Get-Content -LiteralPath $cfg) }
if (-not ($lines | Where-Object { $_ -match '^src\d+=.*crp-sys-img\.xml' })) {
    Write-Host "== 업데이트 사이트 등록: $cfg"
    $count = 0
    $countLine = $lines | Where-Object { $_ -match '^count=(\d+)' } | Select-Object -First 1
    if ($countLine) { $count = [int]($countLine -replace '^count=', '') }
    $n = "{0:D2}" -f $count
    $kept = @($lines | Where-Object { $_ -notmatch '^count=' -and $_ -notmatch '^#' })
    $out = @("## User Sources for Android Repository", "count=$($count + 1)") + $kept + @(
        "src$n=$($UpdateSiteUrl -replace ':', '\:')",
        "disp$n=$UpdateSiteName",
        "enabled$n=true"
    )
    New-Item -ItemType Directory -Force -Path (Split-Path $cfg) | Out-Null
    Set-Content -LiteralPath $cfg -Value $out -Encoding ASCII
}

# 3) system image 설치
$imgDir = Join-Path $SdkRoot "system-images\android-34\pleos_car_emulator\x86_64"
if (Test-Path -LiteralPath (Join-Path $imgDir "system.img")) {
    Write-Host "== 이미 설치됨: $imgDir"
} else {
    Write-Host "== 패키지 설치: $SysImgPackage (약 4.1 GB 다운로드, 네트워크에 따라 수 분~수십 분)"
    if ($AcceptPleosLicense) {
        Write-Host "   -AcceptPleosLicense: 42dot SDK 라이선스 프롬프트에 y 를 자동 입력합니다."
        @("y", "y", "y") | & $sdkmanager --sdk_root="$SdkRoot" $SysImgPackage
    } else {
        Write-Host "   42dot SDK 라이선스 프롬프트가 표시되면 내용을 확인하고 y 를 입력하세요."
        & $sdkmanager --sdk_root="$SdkRoot" $SysImgPackage
    }
    if (-not (Test-Path -LiteralPath (Join-Path $imgDir "system.img"))) {
        throw "시스템 이미지가 설치되지 않았습니다. Android Studio > SDK Manager > SDK Update Sites 에 아래 URL 을 추가한 뒤 'Pleos Connect v2.1.0 APIs' 를 설치하세요: $UpdateSiteUrl"
    }
}

if ($SkipAvd) { Write-Host "== -SkipAvd: AVD 생성 생략"; return }

# 4) AVD 생성
$avdDir = Join-Path $env:USERPROFILE ".android\avd\$AvdName.avd"
if (-not (Test-Path -LiteralPath (Join-Path $avdDir "config.ini"))) {
    # 디바이스 프로필: 이미지에 포함된 devices.xml 의 "Pleos Connect v2.1.0" (id Pleos_Connect_v2_0, 2560x1440 mdpi) 을 우선 사용.
    # Android Studio Device Manager 에서 Automotive > "Pleos Connect v2.1.0" 을 고르는 것과 같습니다.
    $device = "Pleos_Connect_v2_0"
    $deviceIds = @(& $avdmanager list device -c 2>$null | ForEach-Object { "$_".Trim() })
    if ($deviceIds -notcontains $device) {
        Write-Host "   디바이스 프로필 $device 를 찾지 못해 automotive_1024p_landscape 로 대체합니다."
        $device = "automotive_1024p_landscape"
    }
    Write-Host "== AVD 생성: $AvdName (device: $device)"
    # 'custom hardware profile?' 질문에는 no
    "no" | & $avdmanager --silent create avd --name $AvdName --package $SysImgPackage --device $device --force
}
$ini = Join-Path $avdDir "config.ini"
if (-not (Test-Path -LiteralPath $ini)) { throw "AVD 생성 실패: $ini 가 없습니다." }
$content = @(Get-Content -LiteralPath $ini | Where-Object {
    $_ -notmatch '^(hw\.ramSize|fastboot\.forceColdBoot|fastboot\.forceFastBoot|hw\.gpu\.enabled|hw\.gpu\.mode|disk\.dataPartition\.size|hw\.keyboard)='
})
$content += @(
    "hw.ramSize=4096",
    "fastboot.forceColdBoot=yes",
    "fastboot.forceFastBoot=no",
    "hw.gpu.enabled=yes",
    "hw.gpu.mode=host",
    "hw.keyboard=yes",
    "disk.dataPartition.size=8G"
)
Set-Content -LiteralPath $ini -Value $content
Write-Host "== AVD 설정 완료 (Cold boot, RAM 4GB, GPU host): $ini"

Write-Host ""
Write-Host "완료. 실행:  & `"$SdkRoot\emulator\emulator.exe`" -avd $AvdName"
Write-Host "Virtual Console:  adb forward tcp:8088 tcp:8088  ->  http://127.0.0.1:8088/"
