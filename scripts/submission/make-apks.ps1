<#
.SYNOPSIS
  공모전 제출용 release APK 두 개를 만들어 apk/ 폴더에 "<팀명>_<앱이름>_release.apk" 로 둡니다.

.DESCRIPTION
  제출 가이드(https://appdevchallenge.auto-hmg.io/guide/submission): APK 는 실행 가능한 release 버전을
  저장소의 apk/ 폴더에 "팀명_앱이름_release.apk" 로 올립니다.

  - EV Companion : :app:assembleStaging 결과를 release 키로 다시 서명합니다.
      staging = release 와 같은 코드(R8, 비디버그, 디버그 도구 없음)에 중계 서버 주소만 로컬(http://127.0.0.1:8080).
      공개 서버를 아직 배포하지 않았기 때문이며, 심사자는 README 대로 서버를 PC 에서 띄우고 `adb reverse tcp:8080 tcp:8080`
      하면 충전 도우미까지 확인할 수 있습니다. 서버가 없으면 대시보드는 그대로 동작하고 도우미는 "서버에 연결할 수 없어요"를 보여 줍니다.
  - DriveCoach   : :drivecoach:assembleRelease 결과(서명 없음)를 release 키로 서명합니다.
      "폰으로 보기"는 공유 서버 주소(share.releaseBaseUrl)가 없으면 숨겨집니다. 나머지 기능은 모두 차 안에서 동작합니다.

  서명 키는 local.properties(커밋 금지)에서 읽습니다:
    release.storeFile / release.storePassword / release.keyAlias / release.keyPassword / drivecoach.release.keyAlias
  서명은 Gradle 밖에서 apksigner(build-tools)로 합니다. 비밀번호는 환경변수로만 넘깁니다.

.PARAMETER Team
  파일명에 넣을 팀명(개인 참가는 성명).
.PARAMETER SkipBuild
  Gradle 빌드를 건너뛰고 이미 있는 APK 를 서명만 합니다.

.EXAMPLE
  pwsh -File scripts/submission/make-apks.ps1 -Team "우리팀"
#>
param(
    [Parameter(Mandatory = $true)][string]$Team,
    [switch]$SkipBuild,
    [string]$JavaHome = "",
    [string]$SdkRoot = "$env:LOCALAPPDATA\Android\Sdk"
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

if ($Team -notmatch '^[\p{L}\p{N}][\p{L}\p{N}_.-]*$') { throw "팀명에는 글자·숫자·밑줄·점·하이픈만 쓸 수 있습니다: '$Team'" }
if (-not $JavaHome) { $JavaHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "D:\Android Studio\jbr" } }
$java = Join-Path $JavaHome "bin\java.exe"
if (-not (Test-Path -LiteralPath $java)) { throw "JDK 를 찾을 수 없습니다. -JavaHome 으로 지정하세요." }
$env:JAVA_HOME = $JavaHome

# local.properties -> 서명 설정
$props = @{}
foreach ($line in Get-Content -LiteralPath (Join-Path $root "local.properties")) {
    if ($line -match '^\s*([^#=\s][^=]*)=(.*)$') { $props[$Matches[1].Trim()] = $Matches[2].Trim() }
}
foreach ($key in "release.storeFile", "release.storePassword", "release.keyAlias", "drivecoach.release.keyAlias") {
    if (-not $props[$key]) { throw "local.properties 에 $key 가 없습니다 (docs/DEPLOY.md 참고)." }
}
$keystore = $props["release.storeFile"]
if (-not (Test-Path -LiteralPath $keystore)) { throw "키스토어가 없습니다: $keystore" }

$buildTools = Get-ChildItem -LiteralPath (Join-Path $SdkRoot "build-tools") -Directory | Sort-Object Name -Descending | Select-Object -First 1
$apksigner = Join-Path $buildTools.FullName "lib\apksigner.jar"
$zipalign = Join-Path $buildTools.FullName "zipalign.exe"
if (-not (Test-Path -LiteralPath $apksigner)) { throw "apksigner.jar 가 없습니다: $apksigner" }

if (-not $SkipBuild) {
    Push-Location $root
    try {
        & .\gradlew.bat :app:assembleStaging :drivecoach:assembleRelease --console=plain
        if ($LASTEXITCODE -ne 0) { throw "Gradle 빌드 실패 ($LASTEXITCODE)" }
    } finally { Pop-Location }
}

$outDir = Join-Path $root "apk"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$targets = @(
    @{ App = "EVCompanion"; In = "app\build\outputs\apk\staging\app-staging.apk"; Alias = $props["release.keyAlias"] },
    @{ App = "DriveCoach"; In = "drivecoach\build\outputs\apk\release\drivecoach-release-unsigned.apk"; Alias = $props["drivecoach.release.keyAlias"] }
)
$env:PLEOS_KS_PASS = $props["release.storePassword"]
try {
    foreach ($t in $targets) {
        $in = Join-Path $root $t.In
        if (-not (Test-Path -LiteralPath $in)) { throw "빌드 결과가 없습니다: $($t.In)" }
        $out = Join-Path $outDir ("{0}_{1}_release.apk" -f $Team, $t.App)
        & $zipalign -c 4 $in
        if ($LASTEXITCODE -ne 0) { throw "zipalign 검사 실패: $($t.In)" }
        & $java -jar $apksigner sign --ks $keystore --ks-key-alias $t.Alias --ks-pass env:PLEOS_KS_PASS --key-pass env:PLEOS_KS_PASS --out $out $in
        if ($LASTEXITCODE -ne 0) { throw "서명 실패: $($t.App)" }
        $idsig = "$out.idsig"
        if (Test-Path -LiteralPath $idsig) { [System.IO.File]::Delete($idsig) }
        $verify = & $java -jar $apksigner verify --print-certs $out
        if ($LASTEXITCODE -ne 0) { throw "서명 검증 실패: $($t.App)" }
        $item = Get-Item -LiteralPath $out
        "{0}  {1:N1} MB" -f $item.Name, ($item.Length / 1MB)
        "  " + (($verify | Select-String "certificate DN|SHA-256 digest" | ForEach-Object { $_.Line.Trim() }) -join "`n  ")
        "  file SHA-256: " + (Get-FileHash -LiteralPath $out -Algorithm SHA256).Hash.ToLower()
    }
} finally { $env:PLEOS_KS_PASS = $null }
