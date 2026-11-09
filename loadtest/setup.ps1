# Dựng môi trường load test: tạo DB riêng iceenglish_loadtest (KHÔNG đụng DB thật), chạy backend trên cổng 8081
# trỏ vào DB đó rồi nạp dữ liệu seed. In ra PID backend để truyền cho run.ps1.
# Dùng: .\loadtest\setup.ps1   (cần đã build jar: .\mvnw.cmd -DskipTests package)
param(
    [string]$Database = 'iceenglish_loadtest',
    [int]$Port = 8081,
    [string]$Jar = '',
    [string]$Xmx = '1g'
)

$root = Split-Path -Parent $PSScriptRoot
$props = Get-Content (Join-Path $root 'src\main\resources\application.properties') |
    Where-Object { $_ -match '^spring\.datasource\.(username|password)=' }
$env:PGPASSWORD = (($props | Where-Object { $_ -match 'password=' }) -replace '^[^=]+=', '')
$dbUser = ($props | Where-Object { $_ -match 'username=' }) -replace '^[^=]+=', ''
$psql = 'C:\Program Files\PostgreSQL\18\bin\psql.exe'
$jar = if ($Jar) { $Jar } else { Join-Path $root 'target\iceenglish-0.0.1-SNAPSHOT.jar' }
if (-not (Test-Path $jar)) { throw "Jar not found: $jar. Run .\mvnw.cmd -DskipTests package first." }

$exists = & $psql -h localhost -U $dbUser -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='$Database'"
if (-not $exists) {
    & $psql -h localhost -U $dbUser -d postgres -c "CREATE DATABASE $Database" | Out-Null
}
& $psql -h localhost -U $dbUser -d $Database -c 'CREATE EXTENSION IF NOT EXISTS pgcrypto' | Out-Null

# Bảng bài đọc không do Hibernate tạo (dùng JDBC), nên nạp từ migration V6.
$hasReading = & $psql -h localhost -U $dbUser -d $Database -tAc "SELECT to_regclass('public.reading_blogs')"
if (-not $hasReading) {
    & $psql -h localhost -U $dbUser -d $Database -f (Join-Path $root 'src\main\resources\db\migration\V6__Create_reading_library.sql') | Out-Null
}

# Lần chạy đầu Hibernate (ddl-auto=update) tạo bảng và index.
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/$Database"
$env:SERVER_PORT = "$Port"
$env:JAVA_TOOL_OPTIONS = "-Xmx$Xmx"
# Chế độ khách dùng id cố định trong seed.sql (video 1, đề MOCK và GENERAL_EXAM).
$env:APP_GUEST_VIDEO_ID = '11111111-1111-1111-1111-111111111111'
$env:APP_GUEST_EXAM_IDS = '22222222-2222-2222-2222-222222222222,33333333-3333-3333-3333-333333333333'
$log = Join-Path $root "target\loadtest-app-$Port.log"
$proc = Start-Process -FilePath java -ArgumentList '-jar', $jar -RedirectStandardOutput $log `
    -RedirectStandardError (Join-Path $root "target\loadtest-app-$Port.err") -PassThru -WindowStyle Hidden
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 2
    if ((Test-Path $log) -and (Select-String -Path $log -Pattern 'Started IceenglishApplication' -Quiet)) { break }
}

function Invoke-Seed($file) {
    & $psql -h localhost -U $dbUser -d $Database -v ON_ERROR_STOP=1 -f (Join-Path $PSScriptRoot $file) | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Seed $file failed (exit $LASTEXITCODE)." }
}

$users = & $psql -h localhost -U $dbUser -d $Database -tAc 'SELECT count(*) FROM users'
if ([int]$users -eq 0) { Invoke-Seed 'seed.sql' }
Invoke-Seed 'seed-reading.sql'
Write-Host "Load test backend: PID=$($proc.Id), port $Port, DB $Database. Next: .\loadtest\run.ps1 -AppPid $($proc.Id)"
