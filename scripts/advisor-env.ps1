<#
.SYNOPSIS
  deploy/advisor.env 의 값(KEY=VALUE)을 현재 PowerShell 프로세스의 환경변수로 불러옵니다.

.DESCRIPTION
  Docker 배포(docker-compose 의 env_file)와 같은 파일을 로컬 실행(:server:run, keco-probe.ps1)에서도 씁니다.
  값은 화면에 출력하지 않고 불러온 이름만 알려 줍니다. 이미 설정된 환경변수, 빈 값, # 주석 줄은 건너뜁니다.
  advisor.env 는 *.env 로 gitignore 되어 있습니다.

.EXAMPLE
  .\scripts\advisor-env.ps1; .\gradlew.bat :server:run
#>
param(
    [string]$Path = (Join-Path $PSScriptRoot "..\deploy\advisor.env")
)

$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $Path)) {
    Write-Warning "$Path 가 없습니다. deploy\advisor.env.example 을 advisor.env 로 복사해 채우세요."
    return
}

# Environment variables belong to the process, so the caller sees them although this script runs in its own scope
# (no dot-sourcing, which would also leave the last value in a caller variable).
$loaded = foreach ($line in Get-Content -LiteralPath $Path -Encoding utf8) {
    $line = $line.Trim()
    $eq = $line.IndexOf('=')
    if ($line.StartsWith('#') -or $eq -lt 1) { continue }
    $name = $line.Substring(0, $eq).Trim()
    $value = $line.Substring($eq + 1).Trim()
    if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
        $value = $value.Substring(1, $value.Length - 2)
    }
    if (-not $value -or [Environment]::GetEnvironmentVariable($name, 'Process')) { continue }
    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
    $name
}
if ($loaded) { Write-Host "$(Split-Path -Leaf $Path): $($loaded -join ', ') 불러옴" }
