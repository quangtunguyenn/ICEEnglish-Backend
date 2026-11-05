# Dựng môi trường load test: tạo DB riêng iceenglish_loadtest (KHÔNG đụng DB thật), chạy backend trên cổng 8081
# trỏ vào DB đó rồi nạp dữ liệu seed. In ra PID backend để truyền cho run.ps1.
# Dùng: .\loadtest\setup.ps1   (cần đã build jar: .\mvnw.cmd -DskipTests package)
param(
    [string]$Database = 'iceenglish_loadtest',
    [int]$Port = 8081
)

$root = Split-Path -Parent $PSScriptRoot
$props = Get-Content (Join-Path $root 'src\main\resources\application.properties') |
    Where-Object { $_ -match '^spring\.datasource\.(username|password)=' }
$env:PGPASSWORD = (($props | Where-Object { $_ -match 'password=' }) -replace '^[^=]+=', '')
$dbUser = ($props | Where-Object { $_ -match 'username=' }) -replace '^[^=]+=', ''
$psql = 'C:\Program Files\PostgreSQL\18\bin\psql.exe'
$jar = Join-Path $root 'target\iceenglish-0.0.1-SNAPSHOT.jar'
if (-not (Test-Path $jar)) { throw "Jar not found: $jar. Run .\mvnw.cmd -DskipTests package first." }

$exists = & $psql -h localhost -U $dbUser -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='$Database'"
if (-not $exists) {
    & $psql -h localhost -U $dbUser -d postgres -c "CREATE DATABASE $Database" | Out-Null
}
& $psql -h localhost -U $dbUser -d $Database -c 'CREATE EXTENSION IF NOT EXISTS pgcrypto' | Out-Null

# Lần chạy đầu Hibernate (ddl-auto=update) tạo bảng và index.
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/$Database"
$env:SERVER_PORT = "$Port"
$env:JAVA_TOOL_OPTIONS = '-Xmx1g'
$log = Join-Path $root 'target\loadtest-app.log'
$proc = Start-Process -FilePath java -ArgumentList '-jar', $jar -RedirectStandardOutput $log `
    -RedirectStandardError (Join-Path $root 'target\loadtest-app.err') -PassThru -WindowStyle Hidden
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 2
    if ((Test-Path $log) -and (Select-String -Path $log -Pattern 'Started IceenglishApplication' -Quiet)) { break }
}

$users = & $psql -h localhost -U $dbUser -d $Database -tAc 'SELECT count(*) FROM users'
if ([int]$users -eq 0) {
    & $psql -h localhost -U $dbUser -d $Database -f (Join-Path $PSScriptRoot 'seed.sql') | Out-Null
}
Write-Host "Load test backend: PID=$($proc.Id), port $Port, DB $Database. Next: .\loadtest\run.ps1 -AppPid $($proc.Id)"
