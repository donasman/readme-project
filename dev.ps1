# 개발용 DB·메일 수신기, 서버(8080), 화면(5173)을 한 번에 띄운다.
#   켜기: powershell -ExecutionPolicy Bypass -File .\dev.ps1
#   끄기: 서버·화면 창을 닫고 docker compose down

# Vite 는 경로를 글자 그대로 비교해서 c:\dev 처럼 대소문자가 다르면 React 가 두 번 올라가 화면이 빈다. 디스크에 있는 이름으로 맞춘다.
function Get-DiskPath([string]$path) {
  $dir = Get-Item -LiteralPath $path
  $names = @()
  while ($dir.Parent) {
    $names = @($dir.Parent.GetDirectories($dir.Name)[0].Name) + $names
    $dir = $dir.Parent
  }
  Join-Path $dir.Name.ToUpper() ($names -join '\')
}

$root = Get-DiskPath $PSScriptRoot
Set-Location $root

docker info *> $null
if ($LASTEXITCODE -ne 0) {
  Write-Host 'Docker Desktop 을 켠다.'
  Start-Process "$env:ProgramFiles\Docker\Docker\Docker Desktop.exe"
  $deadline = (Get-Date).AddMinutes(3)
  do {
    Start-Sleep -Seconds 3
    docker info *> $null
  } while ($LASTEXITCODE -ne 0 -and (Get-Date) -lt $deadline)
  if ($LASTEXITCODE -ne 0) { throw 'Docker 가 3분 안에 뜨지 않았다.' }
}

docker compose up -d --wait db
if ($LASTEXITCODE -ne 0) { throw 'DB 를 띄우지 못했다.' }

$mailPort = 1025
docker compose up -d mail
if ($LASTEXITCODE -ne 0) {
  # Oracle(TNSLSNR) 이 1025 를 잡고 있을 때다. Oracle 은 두고 메일 수신기를 1026 에 따로 띄운다(docker compose down 으로는 안 꺼진다: docker stop readme-mail-1026).
  Write-Host '1025 를 다른 프로그램이 쓰고 있어 메일 수신기를 1026 에 띄운다.'
  $mailPort = 1026
  if (-not (docker ps -q -f name=^readme-mail-1026$)) {
    docker run -d --rm --name readme-mail-1026 -p 127.0.0.1:1026:1025 -p 127.0.0.1:8025:8025 axllent/mailpit
    if ($LASTEXITCODE -ne 0) { throw '메일 수신기를 띄우지 못했다.' }
  }
}

Start-Process powershell -WorkingDirectory "$root\backend" -ArgumentList '-NoExit', '-Command',
  "`$env:SPRING_MAIL_PORT = '$mailPort'; .\gradlew bootRun"
Start-Process powershell -WorkingDirectory "$root\frontend" -ArgumentList '-NoExit', '-Command',
  'if (-not (Test-Path node_modules)) { npm ci }; npm run dev:server -- --port 5173 --strictPort'

Write-Host "서버 http://localhost:8080 · 화면 http://localhost:5173 · 메일 http://localhost:8025 (서버가 뜨는 데 시간이 걸린다)"
