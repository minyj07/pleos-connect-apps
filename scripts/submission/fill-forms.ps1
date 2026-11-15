<#
.SYNOPSIS
  공모전 접수양식(DOCX) 두 개(EV Companion, DriveCoach)의 "02 앱 프로젝트 소개" 1~7번 칸을 기획서 내용으로 채웁니다.

.DESCRIPTION
  - 양식 원본: submission/templates/contest-application-consent.docx
      (참가 정보와 서명을 채운 사본. 개인정보가 들어 있으므로 submission/ 폴더는 커밋하지 않습니다)
  - 기획서 내용: submission/ev-companion/proposal.md, submission/drivecoach/proposal.md
  - 결과: submission/ev-companion/contest-application-consent EV.docx, submission/drivecoach/contest-application-consent Drive.docx
  양식의 다른 부분(참가 정보, 서명, 동의서, 8번 숏폼 링크)은 그대로 둡니다.
  Python 3 + Pillow 가 필요합니다.

.PARAMETER Team
  팀명. 주면 양식의 팀명 칸 두 곳과 APK 경로(apk/<팀명>_<앱>_release.apk)에 넣습니다.
.PARAMETER RepoUrl
  GitHub 저장소 주소. 비우면 7번 칸에 "(접수 메일에 기재)"로 들어갑니다.
.PARAMETER Commit
  심사 기준 커밋 해시.
.PARAMETER EvVideo
  EV Companion 시연 영상의 링크. 비우면 7번 칸에 "접수 메일에 첨부(EVCompanion_demo.mp4)"로 들어갑니다.
.PARAMETER DcVideo
  DriveCoach 시연 영상의 링크. 비우면 "접수 메일에 첨부(DriveCoach_demo.mp4)".

.EXAMPLE
  pwsh -File scripts/submission/fill-forms.ps1 -Team "우리팀" -RepoUrl "https://github.com/owner/repo" -Commit "abc1234"
#>
param(
    [string]$Team = "",
    [string]$RepoUrl = "",
    [string]$Commit = "",
    [string]$EvVideo = "",
    [string]$DcVideo = "",
    [string]$Template = ""
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $Template) { $Template = Join-Path $root "submission\templates\contest-application-consent.docx" }
if (-not (Test-Path -LiteralPath $Template)) { throw "양식 원본이 없습니다: $Template" }
$filler = Join-Path $PSScriptRoot "fill_form.py"
$teamForFile = if ($Team) { $Team } else { "팀명" }

$common = @()
if ($RepoUrl) { $common += @("--set", "REPO_URL=$RepoUrl") }
if ($Commit) { $common += @("--set", "COMMIT=$Commit") }
if ($Team) { $common += @("--team", $Team) }

$jobs = @(
    @{ Dir = "ev-companion"; Out = "contest-application-consent EV.docx"; App = "EVCompanion"; Name = "";
       Video = $(if ($EvVideo) { $EvVideo } else { "접수 메일에 첨부(EVCompanion_demo.mp4)" }) },
    @{ Dir = "drivecoach"; Out = "contest-application-consent Drive.docx"; App = "DriveCoach"; Name = "DriveCoach (드라이브 코치)";
       Video = $(if ($DcVideo) { $DcVideo } else { "접수 메일에 첨부(DriveCoach_demo.mp4)" }) }
)
foreach ($j in $jobs) {
    $jobArgs = @("-X", "utf8", $filler, "--template", $Template,
        "--proposal", (Join-Path $root "submission\$($j.Dir)\proposal.md"),
        "--out", (Join-Path $root "submission\$($j.Dir)\$($j.Out)"),
        "--root", $root,
        "--set", "APK_PATH=apk/${teamForFile}_$($j.App)_release.apk",
        "--set", "VIDEO=$($j.Video)") + $common
    if ($j.Name) { $jobArgs += @("--app-name", $j.Name) }
    & python @jobArgs
    if ($LASTEXITCODE -ne 0) { throw "양식 채우기 실패: $($j.Dir)" }
}
