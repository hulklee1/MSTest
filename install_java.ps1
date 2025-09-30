# Java 8 자동 설치 스크립트
Write-Host "Edge AI 프로젝트용 Java 8 설치 스크립트" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green

# 관리자 권한 확인
if (-NOT ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole] "Administrator")) {
    Write-Host "관리자 권한이 필요합니다. PowerShell을 관리자로 실행해주세요." -ForegroundColor Red
    exit 1
}

# Java 설치 확인
$javaPath = Get-Command java -ErrorAction SilentlyContinue
if ($javaPath) {
    Write-Host "Java가 이미 설치되어 있습니다: $($javaPath.Source)" -ForegroundColor Yellow
    & java -version
    Write-Host "`n계속하려면 아무 키나 누르세요..."
    $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
} else {
    Write-Host "Java가 설치되어 있지 않습니다." -ForegroundColor Yellow
}

# Chocolatey 설치 확인
if (!(Get-Command choco -ErrorAction SilentlyContinue)) {
    Write-Host "Chocolatey 설치 중..." -ForegroundColor Yellow
    Set-ExecutionPolicy Bypass -Scope Process -Force
    [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072
    iex ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))
    
    # PATH 새로고침
    $env:PATH = [System.Environment]::GetEnvironmentVariable("PATH", "Machine") + ";" + [System.Environment]::GetEnvironmentVariable("PATH", "User")
}

# OpenJDK 8 설치
Write-Host "OpenJDK 8 설치 중..." -ForegroundColor Yellow
choco install openjdk8 -y

# 환경변수 설정
Write-Host "환경변수 설정 중..." -ForegroundColor Yellow

# JAVA_HOME 찾기
$possiblePaths = @(
    "C:\Program Files\OpenJDK\openjdk-8*",
    "C:\Program Files\Java\jdk1.8*",
    "C:\Program Files\Java\jre1.8*",
    "C:\Program Files (x86)\Java\jdk1.8*",
    "C:\Program Files (x86)\Java\jre1.8*"
)

$javaHome = $null
foreach ($path in $possiblePaths) {
    $found = Get-ChildItem $path -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($found) {
        $javaHome = $found.FullName
        break
    }
}

if ($javaHome) {
    Write-Host "Java 설치 경로 발견: $javaHome" -ForegroundColor Green
    
    # 시스템 환경변수 설정
    [Environment]::SetEnvironmentVariable("JAVA_HOME", $javaHome, "Machine")
    
    # PATH에 Java bin 추가
    $currentPath = [Environment]::GetEnvironmentVariable("PATH", "Machine")
    $javaBin = "$javaHome\bin"
    
    if ($currentPath -notlike "*$javaBin*") {
        [Environment]::SetEnvironmentVariable("PATH", "$currentPath;$javaBin", "Machine")
        Write-Host "PATH에 Java bin 디렉토리 추가됨" -ForegroundColor Green
    }
    
    # 현재 세션의 환경변수 업데이트
    $env:JAVA_HOME = $javaHome
    $env:PATH = "$env:PATH;$javaBin"
    
    Write-Host "`nJava 설치 및 설정 완료!" -ForegroundColor Green
    Write-Host "JAVA_HOME: $javaHome" -ForegroundColor Cyan
    
    # Java 버전 확인
    Write-Host "`nJava 버전 확인:" -ForegroundColor Cyan
    & "$javaBin\java.exe" -version
    
} else {
    Write-Host "Java 설치 경로를 찾을 수 없습니다." -ForegroundColor Red
    Write-Host "수동으로 설치해주세요: https://adoptium.net/temurin/releases/?version=8" -ForegroundColor Yellow
}

Write-Host "`n설치가 완료되었습니다. 새로운 PowerShell 창에서 다음을 실행하세요:" -ForegroundColor Green
Write-Host "cd EdgeAIApp" -ForegroundColor Cyan
Write-Host "java -version" -ForegroundColor Cyan
Write-Host ".\gradlew clean build" -ForegroundColor Cyan

Write-Host "`n계속하려면 아무 키나 누르세요..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
