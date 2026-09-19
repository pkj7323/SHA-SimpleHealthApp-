<#
  화면 크기 점검용. 에뮬레이터 하나의 해상도·밀도만 바꿔 대응 기종을 흉내낸다.
  기종마다 AVD를 만들면 한 대에 수 GB씩 먹는데, 레이아웃만 보는 데에는 이걸로 충분하다.

  대응 기종: 갤럭시 S24 · S25 · S26. 세 세대의 기본·Plus·Ultra가 화면만 보면 두 가지다.

  쓰는 법:
    .\tools\screen-profiles.ps1 base     # 기본 모델 크기로
    .\tools\screen-profiles.ps1 ultra    # Ultra·Plus 크기로
    .\tools\screen-profiles.ps1 reset    # 에뮬레이터 원래 크기로
    .\tools\screen-profiles.ps1 list     # 목록만

  물리 기기에는 쓰지 말 것. 기본 대상은 에뮬레이터다.
#>
param(
    [Parameter(Position = 0)][string]$Name = 'list',
    [string]$Serial = 'emulator-5554'
)

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

$profiles = [ordered]@{
    'base'  = @{ size = '1080x2340'; density = 480; note = 'S24/S25/S26 기본 모델 (6.2", 19.5:9)' }
    'ultra' = @{ size = '1440x3120'; density = 505; note = 'Ultra·Plus (6.7~6.9", 고해상도)' }
}

if ($Name -eq 'list') {
    $profiles.GetEnumerator() | ForEach-Object {
        '{0,-6} {1,-10} {2,3}dpi  {3}' -f $_.Key, $_.Value.size, $_.Value.density, $_.Value.note
    }
    return
}

if ($Name -eq 'reset') {
    & $adb -s $Serial shell wm size reset
    & $adb -s $Serial shell wm density reset
    '원래 크기로 되돌렸습니다.'
    return
}

if (-not $profiles.Contains($Name)) {
    "그런 이름은 없습니다: $Name"
    '이름 목록: ' + ($profiles.Keys -join ', ') + ', reset'
    exit 1
}

$p = $profiles[$Name]
& $adb -s $Serial shell wm size $p.size
& $adb -s $Serial shell wm density $p.density
"$Name  $($p.size)  $($p.density)dpi  — $($p.note)"
