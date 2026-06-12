# Medicine Project (약 관리 앱)

Firebase Firestore와 Gemini AI를 활용한 Android 약 관리 앱입니다.

## 주요 기능

- 약 목록 관리 (추가/수정/삭제)
- OCR을 이용한 약 정보 자동 입력
- Gemini AI 기반 약 관련 질의응답
- 복약 알림 및 기록 관리
- 사용자 프로필 관리

## 기술 스택

- **언어**: Java (Android)
- **DB**: Firebase Firestore
- **AI**: Gemini API
- **OCR**: ML Kit (Google)
- **최소 SDK**: Android 8.0 (API 26)

## 시작하기

### 사전 준비

1. Firebase 프로젝트 생성 및 Android 앱 등록
2. `google-services.json` 파일 다운로드 후 `app/` 폴더에 추가
3. Gemini API 키 발급

### 설치

```bash
git clone https://github.com/YOUR_USERNAME/Medicine_Project.git
cd Medicine_Project
```

Android Studio에서 프로젝트를 열고 빌드하세요.

> **주의**: `google-services.json` 파일은 보안상 `.gitignore`에 포함되어 있습니다.  
> Firebase 콘솔에서 직접 다운로드하여 `app/` 폴더에 배치하세요.

## 프로젝트 구조

```
app/src/main/
├── java/com/medicine/app/
│   ├── activities/     # Activity 클래스
│   ├── adapters/       # RecyclerView Adapter
│   ├── fragments/      # Fragment 클래스
│   ├── models/         # 데이터 모델
│   └── receivers/      # BroadcastReceiver
└── res/
    ├── layout/         # XML 레이아웃
    ├── drawable/       # 아이콘 및 배경
    └── values/         # 색상, 문자열, 테마
```
