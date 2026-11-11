# Windows PowerShell 5.1 전용 (WinRT 프로젝션). ffmpeg 없이 Windows 내장 Windows.Media.Editing 으로 영상을 합성합니다.
#   powershell.exe -NoProfile -ExecutionPolicy Bypass -File video-compose.ps1 -Edl edit.json [-ThumbDir dir -ThumbAt "1,5,10"] [-NoRender]
#
# edit.json:
#   { "output": "C:/.../demo.mp4", "width": 1920, "height": 1200, "fps": 30, "bitrate": 8000000,
#     "base":   [ { "image": "bg.png", "duration": 120.0 } ],                      // 바탕(순서대로 이어 붙임)
#     "layers": [ [ { "file": "take1.mp4", "start": 2.0, "end": 60.0, "at": 3.0, "rect": [0,0,1920,1080] } ],
#                 [ { "image": "cap01.png", "duration": 5.0, "at": 3.0, "rect": [0,1080,1920,120] } ] ] }
#   start/end 는 원본 영상 안의 초, at 은 결과 영상 타임라인의 초, rect 는 [x, y, w, h] 픽셀입니다.
#   상대 경로는 edit.json 이 있는 폴더 기준입니다.
param(
    [Parameter(Mandatory = $true)][string]$Edl,
    [string]$ThumbDir = "",
    [string]$ThumbAt = "",          # 쉼표로 나눈 초 목록, 예: "1,3.5,6"
    [int]$ThumbWidth = 0,
    [switch]$NoRender
)
$ErrorActionPreference = "Stop"
$thumbTimes = @()
if ($ThumbAt) { $thumbTimes = @($ThumbAt.Split(',') | ForEach-Object { [double]::Parse($_.Trim(), [Globalization.CultureInfo]::InvariantCulture) }) }

Add-Type -AssemblyName System.Runtime.WindowsRuntime
$methods = [System.WindowsRuntimeSystemExtensions].GetMethods()
$asTaskOp = ($methods | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
$asTaskOpProgress = ($methods | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperationWithProgress`2' })[0]
function Await($op, [Type]$type) { $t = $asTaskOp.MakeGenericMethod($type).Invoke($null, @($op)); $t.Wait(-1) | Out-Null; $t.Result }
function AwaitProgress($op, [Type]$result, [Type]$progress) { $t = $asTaskOpProgress.MakeGenericMethod($result, $progress).Invoke($null, @($op)); $t.Wait(-1) | Out-Null; $t.Result }

[Windows.Storage.StorageFile, Windows.Storage, ContentType = WindowsRuntime] | Out-Null
[Windows.Storage.StorageFolder, Windows.Storage, ContentType = WindowsRuntime] | Out-Null
[Windows.Storage.CreationCollisionOption, Windows.Storage, ContentType = WindowsRuntime] | Out-Null
[Windows.Media.Editing.MediaComposition, Windows.Media.Editing, ContentType = WindowsRuntime] | Out-Null
[Windows.Media.Editing.MediaClip, Windows.Media.Editing, ContentType = WindowsRuntime] | Out-Null
[Windows.Media.Editing.MediaOverlay, Windows.Media.Editing, ContentType = WindowsRuntime] | Out-Null
[Windows.Media.Editing.MediaOverlayLayer, Windows.Media.Editing, ContentType = WindowsRuntime] | Out-Null
[Windows.Media.Editing.MediaTrimmingPreference, Windows.Media.Editing, ContentType = WindowsRuntime] | Out-Null
[Windows.Media.Editing.VideoFramePrecision, Windows.Media.Editing, ContentType = WindowsRuntime] | Out-Null
[Windows.Media.MediaProperties.MediaEncodingProfile, Windows.Media.MediaProperties, ContentType = WindowsRuntime] | Out-Null
[Windows.Media.MediaProperties.VideoEncodingQuality, Windows.Media.MediaProperties, ContentType = WindowsRuntime] | Out-Null
[Windows.Media.Transcoding.TranscodeFailureReason, Windows.Media.Transcoding, ContentType = WindowsRuntime] | Out-Null
[Windows.Graphics.Imaging.ImageStream, Windows.Graphics.Imaging, ContentType = WindowsRuntime] | Out-Null
# Windows.Foundation.Rect 는 System.Runtime.WindowsRuntime 어셈블리의 .NET 형식이라 따로 불러올 필요가 없습니다.

# PowerShell 의 멤버 접근은 WinRT 컬렉션·일부 속성에서 null 이나 __ComObject 를 돌려주므로 리플렉션으로 직접 다룹니다.
# 함수가 컬렉션을 그대로 내보내면 PowerShell 이 풀어 버려(빈 목록은 null) 단항 쉼표로 감싸 돌려줍니다.
function Raw($o) { if ($o -is [psobject]) { , $o.PSObject.BaseObject } else { , $o } }
function Get-P([Type]$type, $obj, [string]$name) { , $type.GetProperty($name).GetValue((Raw $obj)) }
function Set-P([Type]$type, $obj, [string]$name, $value) { $type.GetProperty($name).SetValue((Raw $obj), (Raw $value)) }
function Add-Item($list, [Type]$itemType, $item) {
    [System.Collections.Generic.ICollection`1].MakeGenericType($itemType).GetMethod('Add').Invoke((Raw $list), @((Raw $item))) | Out-Null
}
$tComp = [Windows.Media.Editing.MediaComposition]
$tClip = [Windows.Media.Editing.MediaClip]
$tOverlay = [Windows.Media.Editing.MediaOverlay]
$tLayer = [Windows.Media.Editing.MediaOverlayLayer]

function Get-File([string]$path) {
    $full = [System.IO.Path]::GetFullPath($path)      # WinRT 는 역슬래시 절대 경로만 받습니다
    Await ([Windows.Storage.StorageFile]::GetFileFromPathAsync($full)) ([Windows.Storage.StorageFile])
}
function Seconds([double]$s) { [TimeSpan]::FromMilliseconds([math]::Round($s * 1000)) }
function Resolve-Item([string]$p, [string]$baseDir) { if ([System.IO.Path]::IsPathRooted($p)) { $p } else { Join-Path $baseDir $p } }

function New-Clip($item, [string]$baseDir) {
    if ($item.image) {
        return Await ([Windows.Media.Editing.MediaClip]::CreateFromImageFileAsync((Get-File (Resolve-Item $item.image $baseDir)), (Seconds $item.duration))) $tClip
    }
    $clip = Await ([Windows.Media.Editing.MediaClip]::CreateFromFileAsync((Get-File (Resolve-Item $item.file $baseDir)))) $tClip
    $total = (Get-P $tClip $clip 'OriginalDuration').TotalSeconds
    $start = if ($null -ne $item.start) { [double]$item.start } else { 0.0 }
    $end = if ($null -ne $item.end) { [math]::Min([double]$item.end, $total) } else { $total }
    if ($start -gt 0) { Set-P $tClip $clip 'TrimTimeFromStart' (Seconds $start) }
    if ($total - $end -gt 0.001) { Set-P $tClip $clip 'TrimTimeFromEnd' (Seconds ($total - $end)) }
    return $clip
}

$edlPath = (Resolve-Path -LiteralPath $Edl).Path
$baseDir = Split-Path -Parent $edlPath
$spec = Get-Content -LiteralPath $edlPath -Raw -Encoding UTF8 | ConvertFrom-Json

$comp = Raw (New-Object Windows.Media.Editing.MediaComposition)
$clips = Get-P $tComp $comp 'Clips'
$layers = Get-P $tComp $comp 'OverlayLayers'
$baseCount = 0; $overlayCount = 0
foreach ($item in $spec.base) { Add-Item $clips $tClip (New-Clip $item $baseDir); $baseCount++ }
foreach ($layerSpec in $spec.layers) {
    $layer = Raw (New-Object Windows.Media.Editing.MediaOverlayLayer)
    $overlays = Get-P $tLayer $layer 'Overlays'
    foreach ($item in $layerSpec) {
        $overlay = Raw (New-Object Windows.Media.Editing.MediaOverlay -ArgumentList (New-Clip $item $baseDir))
        $r = $item.rect
        Set-P $tOverlay $overlay 'Position' ([Windows.Foundation.Rect]::new([double]$r[0], [double]$r[1], [double]$r[2], [double]$r[3]))
        Set-P $tOverlay $overlay 'Delay' (Seconds $item.at)
        Set-P $tOverlay $overlay 'Opacity' ([double]1.0)
        Set-P $tOverlay $overlay 'AudioEnabled' $false
        Add-Item $overlays $tOverlay $overlay
        $overlayCount++
    }
    Add-Item $layers $tLayer $layer
}
"composition duration: {0:N2} s, base clips: {1}, overlays: {2}" -f (Get-P $tComp $comp 'Duration').TotalSeconds, $baseCount, $overlayCount

if ($ThumbDir -and $thumbTimes.Count -gt 0) {
    New-Item -ItemType Directory -Force -Path $ThumbDir | Out-Null
    foreach ($t in $thumbTimes) {
        $img = Await ($comp.GetThumbnailAsync((Seconds $t), $ThumbWidth, 0, [Windows.Media.Editing.VideoFramePrecision]::NearestFrame)) ([Windows.Graphics.Imaging.ImageStream])
        $ext = if ($img.ContentType -match 'png') { 'png' } elseif ($img.ContentType -match 'jp') { 'jpg' } else { 'bmp' }
        $out = Join-Path $ThumbDir ("t{0:000.0}.{1}" -f $t, $ext)
        $net = [System.IO.WindowsRuntimeStreamExtensions]::AsStreamForRead($img.GetInputStreamAt(0))
        $fs = [System.IO.File]::Create($out); $net.CopyTo($fs); $fs.Close(); $net.Close()
        "thumb $out ($($img.ContentType), $($img.Width)x$($img.Height))"
    }
}

if (-not $NoRender) {
    $tProfile = [Windows.Media.MediaProperties.MediaEncodingProfile]
    $profile = [Windows.Media.MediaProperties.MediaEncodingProfile]::CreateMp4([Windows.Media.MediaProperties.VideoEncodingQuality]::HD1080p)
    $video = Get-P $tProfile $profile 'Video'
    $tVideo = $video.GetType()
    Set-P $tVideo $video 'Width' ([uint32]$spec.width)
    Set-P $tVideo $video 'Height' ([uint32]$spec.height)
    Set-P $tVideo $video 'Bitrate' ([uint32]$spec.bitrate)
    $rate = Get-P $tVideo $video 'FrameRate'
    Set-P ($rate.GetType()) $rate 'Numerator' ([uint32]$spec.fps)
    Set-P ($rate.GetType()) $rate 'Denominator' ([uint32]1)
    $outPath = [System.IO.Path]::GetFullPath($spec.output)
    $outDir = Split-Path -Parent $outPath
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null
    $folder = Await ([Windows.Storage.StorageFolder]::GetFolderFromPathAsync($outDir)) ([Windows.Storage.StorageFolder])
    $file = Await ($folder.CreateFileAsync((Split-Path -Leaf $outPath), [Windows.Storage.CreationCollisionOption]::ReplaceExisting)) ([Windows.Storage.StorageFile])
    $sw = [Diagnostics.Stopwatch]::StartNew()
    $reason = AwaitProgress ($comp.RenderToFileAsync($file, [Windows.Media.Editing.MediaTrimmingPreference]::Precise, $profile)) ([Windows.Media.Transcoding.TranscodeFailureReason]) ([double])
    "render: $reason in {0:N1} s -> {1} ({2:N1} MB)" -f $sw.Elapsed.TotalSeconds, $outPath, ((Get-Item -LiteralPath $outPath).Length / 1MB)
    if ("$reason" -ne "None") { exit 1 }
}
