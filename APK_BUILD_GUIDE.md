# EdgeAI 모바일 앱 APK 빌드 가이드

## 개요
이 가이드는 EdgeAI Android 앱을 APK 파일로 빌드하여 배포하는 방법을 설명합니다.

## 필요 조건
- Android Studio 설치
- Android SDK 설치
- Java Development Kit (JDK) 8 이상

## APK 빌드 방법

### 1. Android Studio를 통한 빌드

#### Debug APK 생성 (테스트용)
1. Android Studio에서 프로젝트 열기
2. 상단 메뉴: `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`
3. 빌드 완료 후 `locate` 링크 클릭하여 APK 파일 위치 확인
4. APK 위치: `app/build/outputs/apk/debug/app-debug.apk`

#### Release APK 생성 (배포용)
1. **키 스토어 생성 (최초 1회만)**
   - 상단 메뉴: `Build` → `Generate Signed Bundle / APK`
   - `APK` 선택 → `Next`
   - `Create new...` 클릭
   - 키 스토어 정보 입력:
     ```
     Key store path: EdgeAI-release-key.jks
     Password: [안전한 비밀번호]
     Key alias: edgeai-key
     Key password: [키 비밀번호]
     Validity (years): 25
     ```
   - Certificate 정보 입력 (조직명, 국가 등)
   - `OK` 클릭

2. **Release APK 빌드**
   - 상단 메뉴: `Build` → `Generate Signed Bundle / APK`
   - `APK` 선택 → `Next`
   - 생성한 키 스토어 파일 선택 및 비밀번호 입력
   - `Next` → `release` 선택 → `Finish`
   - APK 위치: `app/build/outputs/apk/release/app-release.apk`

### 2. 커맨드 라인을 통한 빌드

#### Debug APK
```bash
# 프로젝트 루트 디렉토리에서 실행
cd EdgeAIApp
./gradlew assembleDebug

# Windows의 경우
gradlew.bat assembleDebug
```

#### Release APK (서명된 APK)
```bash
# 키 스토어 생성
keytool -genkey -v -keystore EdgeAI-release-key.jks -keyalg RSA -keysize 2048 -validity 9125 -alias edgeai-key

# Release APK 빌드
./gradlew assembleRelease

# Windows의 경우
gradlew.bat assembleRelease
```

### 3. 키 스토어 설정 파일 (gradle.properties)

프로젝트 루트에 `gradle.properties` 파일 생성 또는 수정:

```properties
# 키 스토어 설정
KEYSTORE_FILE=EdgeAI-release-key.jks
KEYSTORE_PASSWORD=your_keystore_password
KEY_ALIAS=edgeai-key
KEY_PASSWORD=your_key_password
```

### 4. build.gradle 설정 (app/build.gradle)

```gradle
android {
    ...
    
    signingConfigs {
        release {
            storeFile file(project.property('KEYSTORE_FILE'))
            storePassword project.property('KEYSTORE_PASSWORD')
            keyAlias project.property('KEY_ALIAS')
            keyPassword project.property('KEY_PASSWORD')
        }
    }
    
    buildTypes {
        release {
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
            signingConfig signingConfigs.release
        }
    }
}
```

## APK 최적화 설정

### ProGuard 설정 (proguard-rules.pro)
```proguard
# EdgeAI 앱 특정 설정
-keep class com.example.edgeaiapp.** { *; }
-keep class retrofit2.** { *; }
-keep class com.google.gson.** { *; }

# 일반적인 최적화 설정
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn retrofit2.**
```

## 빌드 결과물

### Debug APK
- **파일명**: `app-debug.apk`
- **위치**: `app/build/outputs/apk/debug/`
- **용도**: 개발 및 테스트용
- **서명**: 자동 생성된 디버그 키로 서명
- **크기**: 약 8-15MB (최적화 없음)

### Release APK
- **파일명**: `app-release.apk`
- **위치**: `app/build/outputs/apk/release/`
- **용도**: 배포용
- **서명**: 사용자 생성 키로 서명
- **크기**: 약 5-8MB (최적화됨)

## 배포 방법

### 1. 직접 배포
- APK 파일을 대상 디바이스에 전송
- 디바이스에서 `설정` → `보안` → `알 수 없는 소스 허용` 활성화
- APK 파일 실행하여 설치

### 2. 웹 서버를 통한 배포
```bash
# 웹 서버에 APK 파일 업로드
scp app-release.apk user@your-server:/var/www/html/downloads/

# 다운로드 링크 제공
https://your-server.com/downloads/app-release.apk
```

### 3. QR 코드를 통한 배포
- APK 다운로드 링크를 QR 코드로 생성
- 사용자가 QR 코드 스캔하여 직접 설치

## 버전 관리

### 앱 버전 업데이트 (app/build.gradle)
```gradle
android {
    defaultConfig {
        applicationId "com.example.edgeaiapp"
        minSdkVersion 26
        targetSdkVersion 34
        versionCode 2  // 빌드 번호 증가
        versionName "1.1"  // 사용자 버전 업데이트
    }
}
```

## 테스트 체크리스트

APK 배포 전 확인사항:

- [ ] 모든 기능 정상 동작 확인
- [ ] 다양한 디바이스에서 테스트
- [ ] 네트워크 연결 상태별 테스트
- [ ] 엣지서버 연결 설정 테스트
- [ ] 이미지 분석 기능 테스트
- [ ] 최종 전송 기능 테스트

## 트러블슈팅

### 빌드 오류
```bash
# 캐시 정리
./gradlew clean

# 의존성 다시 다운로드
./gradlew build --refresh-dependencies
```

### 설치 오류
- 이전 버전 제거 후 재설치
- 디바이스 저장 공간 확인
- 안드로이드 버전 호환성 확인

## 보안 고려사항

1. **키 스토어 보안**
   - 키 스토어 파일과 비밀번호 안전하게 보관
   - 백업 저장 권장

2. **네트워크 보안**
   - HTTPS 사용 권장
   - 인증서 검증 설정

3. **앱 보안**
   - ProGuard 활성화로 코드 난독화
   - 민감한 정보 하드코딩 금지

## 자동화 스크립트

### 배포용 APK 빌드 스크립트 (build_release.sh)
```bash
#!/bin/bash
echo "Building EdgeAI Release APK..."

# 프로젝트 디렉토리로 이동
cd EdgeAIApp

# 클린 빌드
./gradlew clean

# Release APK 빌드
./gradlew assembleRelease

# 결과 확인
if [ -f "app/build/outputs/apk/release/app-release.apk" ]; then
    echo "✅ APK 빌드 성공!"
    echo "📁 위치: app/build/outputs/apk/release/app-release.apk"
    
    # 파일 크기 확인
    ls -lh app/build/outputs/apk/release/app-release.apk
else
    echo "❌ APK 빌드 실패!"
fi
```

이제 EdgeAI 앱을 APK로 빌드하여 배포할 수 있습니다! 🚀
