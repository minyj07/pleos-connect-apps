<#
.SYNOPSIS
  한국환경공단 전기자동차 충전소 정보 API(data.go.kr 15076352) 실측 스크립트 (Phase 0-4).

.DESCRIPTION
  https://apis.data.go.kr/B552584/EvCharger 의 getChargerInfo / getChargerStatus 를 호출해
  응답 형식(JSON/XML, 단건/배열, 오류 형태), 응답 시간·크기, zcode 필수 여부, 강원(42/51)·전북(45/52) 신·구 코드,
  zscode 지원 여부를 확인하고, 서버 파싱 테스트용 fixture 를 server/src/test/resources/fixtures/ 에 저장합니다.

  인증키는 DATA_GO_KR_SERVICE_KEY 의 "Decoding 키"를 씁니다 (Process → User 환경변수 → deploy\advisor.env 순으로 찾음).
    deploy\advisor.env:  DATA_GO_KR_SERVICE_KEY=<Decoding 키>
  키는 화면에 출력하지 않으며, 저장하는 응답에서도 키 문자열을 <SERVICE_KEY> 로 바꿉니다.
  개발계정 트래픽은 하루 10,000 회입니다. 이 스크립트는 한 번에 약 15 회 호출합니다.

.EXAMPLE
  pwsh -File scripts/keco-probe.ps1
  pwsh -File scripts/keco-probe.ps1 -SkipLargePage     # 9,999 행 페이지 측정 생략
#>
param(
    [switch]$SkipLargePage
)

$ErrorActionPreference = 'Stop'
$base = "https://apis.data.go.kr/B552584/EvCharger"
$fixtures = Join-Path $PSScriptRoot "..\server\src\test\resources\fixtures"
New-Item -ItemType Directory -Force $fixtures | Out-Null

$key = [Environment]::GetEnvironmentVariable("DATA_GO_KR_SERVICE_KEY", "Process")
if (-not $key) { $key = [Environment]::GetEnvironmentVariable("DATA_GO_KR_SERVICE_KEY", "User") }
if (-not $key) { & (Join-Path $PSScriptRoot "advisor-env.ps1"); $key = $env:DATA_GO_KR_SERVICE_KEY }
if (-not $key) { throw "DATA_GO_KR_SERVICE_KEY 가 없습니다. data.go.kr 에서 받은 Decoding 키를 deploy\advisor.env 에 넣으세요." }
$encodedKey = [uri]::EscapeDataString($key)

function Invoke-Keco([string]$operation, [hashtable]$query, [string]$serviceKey = $encodedKey) {
    $qs = ($query.GetEnumerator() | Sort-Object Name | ForEach-Object { "$($_.Name)=$([uri]::EscapeDataString([string]$_.Value))" }) -join '&'
    $url = "$base/$operation`?serviceKey=$serviceKey&$qs"
    $sw = [Diagnostics.Stopwatch]::StartNew()
    try {
        $r = Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 120
        $status = [int]$r.StatusCode
        $body = $r.Content
    } catch {
        $status = [int]$_.Exception.Response.StatusCode
        $body = "$($_.ErrorDetails.Message)"
    }
    $sw.Stop()
    # Never persist the key, whichever form it appears in.
    $clean = $body.Replace($key, '<SERVICE_KEY>').Replace($encodedKey, '<SERVICE_KEY>')
    [pscustomobject]@{ Status = $status; Ms = [int]$sw.Elapsed.TotalMilliseconds; Bytes = [Text.Encoding]::UTF8.GetByteCount($body); Body = $clean }
}

function Summary([string]$label, $res) {
    $total = if ($res.Body -match '"totalCount"\s*:\s*"?(\d+)') { $Matches[1] } elseif ($res.Body -match '<totalCount>(\d+)</totalCount>') { $Matches[1] } else { '-' }
    $code = if ($res.Body -match '"resultCode"\s*:\s*"?(\w+)') { $Matches[1] } elseif ($res.Body -match '<(resultCode|returnReasonCode)>(\w+)<') { $Matches[2] } else { '-' }
    [pscustomobject]@{ Probe = $label; Http = $res.Status; ResultCode = $code; TotalCount = $total; Ms = $res.Ms; KB = [math]::Round($res.Bytes / 1KB, 1) }
}

function Save([string]$name, $res) { Set-Content -LiteralPath (Join-Path $fixtures $name) -Value $res.Body -Encoding utf8NoBOM }

$rows = @()

$r = Invoke-Keco 'getChargerInfo' @{ pageNo = 1; numOfRows = 10; zcode = 11; dataType = 'JSON' }
Save 'keco-info-seoul-10.json' $r; $rows += Summary 'info zcode=11 JSON 10행' $r

$r = Invoke-Keco 'getChargerInfo' @{ pageNo = 1; numOfRows = 10; zcode = 11 }
Save 'keco-info-seoul-10.xml' $r; $rows += Summary 'info zcode=11 XML(기본) 10행' $r

# 2026-11-04: numOfRows=1 still returns 10 rows (an item array), so a single-object item is never seen here.
$r = Invoke-Keco 'getChargerInfo' @{ pageNo = 1; numOfRows = 1; zcode = 11; dataType = 'JSON' }
$rows += Summary 'info numOfRows=1 (최소 행 수)' $r

$r = Invoke-Keco 'getChargerInfo' @{ pageNo = 1; numOfRows = 10; zcode = 11; zscode = 11680; dataType = 'JSON' }
Save 'keco-info-gangnam-zscode.json' $r; $rows += Summary 'info zcode=11 zscode=11680(강남구)' $r

foreach ($z in 42, 51, 45, 52) {
    $r = Invoke-Keco 'getChargerInfo' @{ pageNo = 1; numOfRows = 10; zcode = $z; dataType = 'JSON' }
    $rows += Summary "info zcode=$z (강원/전북 신·구 코드)" $r
}

$r = Invoke-Keco 'getChargerStatus' @{ pageNo = 1; numOfRows = 100; period = 5; zcode = 11; dataType = 'JSON' }
Save 'keco-status-seoul-period5.json' $r; $rows += Summary 'status zcode=11 period=5' $r

$r = Invoke-Keco 'getChargerStatus' @{ pageNo = 1; numOfRows = 100; period = 5; dataType = 'JSON' }
Save 'keco-status-all-period5.json' $r; $rows += Summary 'status zcode 없음 period=5' $r

$r = Invoke-Keco 'getChargerStatus' @{ pageNo = 1; numOfRows = 9999; period = 10; dataType = 'JSON' }
$rows += Summary 'status zcode 없음 period=10 9,999행' $r

if (-not $SkipLargePage) {
    $r = Invoke-Keco 'getChargerInfo' @{ pageNo = 1; numOfRows = 9999; zcode = 11; dataType = 'JSON' }
    $rows += Summary 'info zcode=11 9,999행 (크기·시간)' $r
}

$r = Invoke-Keco 'getChargerInfo' @{ pageNo = 1; numOfRows = 10; zcode = 11; dataType = 'JSON' } -serviceKey 'INVALID_KEY'
Save 'keco-error-invalid-key.txt' $r; $rows += Summary '잘못된 키 (오류 형태)' $r

$rows | Format-Table -AutoSize
"fixture 저장 위치: $((Resolve-Path $fixtures).Path)"
