# Chạy load test trên DB iceenglish_loadtest (KHÔNG dùng DB thật).
# Dùng: .\loadtest\run.ps1 -AppPid <PID backend> [-Users 1000] [-Base http://localhost:8081]
# Yêu cầu: backend đang chạy với SPRING_DATASOURCE_URL trỏ tới iceenglish_loadtest, DB đã seed (loadtest\seed.sql).
param(
    [Parameter(Mandatory = $true)][int]$AppPid,
    [int]$Users = 1000,
    [string]$Base = 'http://localhost:8081',
    [string]$Database = 'iceenglish_loadtest',
    [string]$Out = ''
)

$root = Split-Path -Parent $PSScriptRoot
$props = Get-Content (Join-Path $root 'src\main\resources\application.properties') |
    Where-Object { $_ -match '^spring\.datasource\.(username|password)=' }
$env:PGPASSWORD = (($props | Where-Object { $_ -match 'password=' }) -replace '^[^=]+=', '')
$dbUser = ($props | Where-Object { $_ -match 'username=' }) -replace '^[^=]+=', ''
$psql = 'C:\Program Files\PostgreSQL\18\bin\psql.exe'

# Mỗi lần chạy phải bắt đầu từ trạng thái chưa có bài nộp (MOCK chỉ được thi 1 lần/học sinh).
& $psql -h localhost -U $dbUser -d $Database -c 'TRUNCATE sub_details, part_time_logs, submissions CASCADE' | Out-Null

$env:APP_PID = $AppPid
if ($Out -and -not [System.IO.Path]::IsPathRooted($Out)) { $Out = Join-Path (Get-Location).Path $Out }
Push-Location (Join-Path $root 'loadtest')
try {
    if ($Out) { node loadtest.mjs $Users $Base 2>&1 | Tee-Object -FilePath $Out } else { node loadtest.mjs $Users $Base }
} finally { Pop-Location }
