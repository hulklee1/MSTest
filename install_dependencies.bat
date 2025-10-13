@echo off
REM ====================================
REM EdgeAI 모바일 앱 의존성 설치 스크립트 (Windows)
REM ====================================

echo [EdgeAI] 개발 환경 의존성 설치를 시작합니다...

REM 관리자 권한 확인
net session >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [WARNING] 관리자 권한이 없습니다. 일부 설치가 실패할 수 있습니다.
    echo [INFO] 관리자 권한으로 실행하는 것을 권장합니다.
    pause
)

REM Chocolatey 설치 확인
where choco >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [INFO] Chocolatey 패키지 매니저를 설치합니다...
    powershell -Command "Set-ExecutionPolicy Bypass -Scope Process -Force; [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072; iex ((New-Object System.Net.WebClient).DownloadString('https://chocolatey.org/install.ps1'))"
    if %ERRORLEVEL% neq 0 (
        echo [ERROR] Chocolatey 설치 실패. 수동으로 설치해주세요.
        goto manual_install
    )
    echo [SUCCESS] Chocolatey 설치 완료!
)

REM Java 17 설치
echo [INFO] Java 17 설치를 확인합니다...
java -version 2>&1 | find "17" >nul
if %ERRORLEVEL% neq 0 (
    echo [INFO] Java 17을 설치합니다...
    choco install openjdk17 -y
    if %ERRORLEVEL% neq 0 (
        echo [ERROR] Java 17 설치 실패
        goto manual_install
    )
    echo [SUCCESS] Java 17 설치 완료!
) else (
    echo [INFO] Java 17이 이미 설치되어 있습니다.
)

REM Android Studio 설치
echo [INFO] Android Studio 설치를 확인합니다...
if not exist "%LOCALAPPDATA%\Android\Sdk" (
    echo [INFO] Android Studio를 설치합니다...
    choco install androidstudio -y
    if %ERRORLEVEL% neq 0 (
        echo [ERROR] Android Studio 설치 실패
        goto manual_install
    )
    echo [SUCCESS] Android Studio 설치 완료!
    echo [INFO] Android Studio를 실행하여 SDK를 설정해주세요.
) else (
    echo [INFO] Android SDK가 이미 설치되어 있습니다.
)

REM Git 설치 (선택사항)
where git >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [INFO] Git을 설치합니다...
    choco install git -y
    if %ERRORLEVEL% neq 0 (
        echo [WARNING] Git 설치 실패. 수동으로 설치해주세요.
    ) else (
        echo [SUCCESS] Git 설치 완료!
    )
) else (
    echo [INFO] Git이 이미 설치되어 있습니다.
)

echo [SUCCESS] 모든 의존성 설치가 완료되었습니다!
echo [INFO] 시스템을 재부팅하거나 새 터미널을 열어주세요.
goto end

:manual_install
echo.
echo ====================================
echo 수동 설치 가이드
echo ====================================
echo 1. Java 17 JDK 설치:
echo    https://adoptium.net/temurin/releases/
echo.
echo 2. Android Studio 설치:
echo    https://developer.android.com/studio
echo.
echo 3. Git 설치 (선택사항):
echo    https://git-scm.com/download/win
echo.
echo 4. 환경변수 설정:
echo    - JAVA_HOME: Java 설치 경로
echo    - ANDROID_HOME: Android SDK 경로
echo    - PATH에 위 경로들의 bin 디렉토리 추가
echo ====================================

:end
pause
