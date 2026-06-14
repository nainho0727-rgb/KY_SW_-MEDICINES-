# MediFit AI (MEDICINES)

Firebase Firestore와 Google Gemini AI를 활용한 **Android 약 복용 관리 앱**입니다.
약봉투를 촬영하면 AI가 약 정보를 자동 인식·등록하고, 복용 알림과 주의사항을 안내합니다.

## 주요 기능

- 약 목록 관리 (추가 / 삭제)
- 약봉투·처방전 촬영 → **Gemini AI가 약 정보 자동 인식·등록** (이미지에서 직접 OCR + 구조화)
- **Gemini AI 기반 약 관련 질의응답** (AI 채팅)
- **약 상호작용·중복 1차 검사** (AI 기반, 참고용 — 전문가 상담 권고)
- **시간대별 복용 알림** 및 복용 기록·통계 관리
- 사용자 프로필 관리

## 기술 스택

- **언어**: Java (Android)
- **DB**: Firebase Firestore
- **AI / OCR**: Google Gemini (멀티모달 — 이미지에서 약 정보 직접 인식, 별도 OCR 엔진 미사용)
- **최소 SDK**: Android 8.0 (API 26)

## 시작하기

### 사전 준비

1. Firebase 프로젝트 생성 및 Android 앱 등록 (패키지명: `com.medicine.app`)
2. `google-services.json` 다운로드 → `app/` 폴더에 배치
3. [Google AI Studio](https://aistudio.google.com)에서 Gemini API 키 발급

### 설치

```bash
git clone https://github.com/YOUR_USERNAME/medifit-ai.git
cd medifit-ai
```

1. 위에서 받은 `google-services.json`을 `app/` 폴더에 배치
2. 프로젝트 루트의 `local.properties` 파일에 Gemini 키 추가:
   ```properties
   GEMINI_API_KEY=발급받은_키
   ```
3. Android Studio에서 프로젝트를 열고 빌드(Run)하세요.

> **보안 안내**: API 키가 들어있는 `local.properties`와 Firebase 설정인 `google-services.json`은
> `.gitignore`에 포함되어 깃허브에 올라가지 않습니다. 각자 위 방법으로 직접 추가해야 빌드됩니다.

## 프로젝트 구조

```
app/src/main/
├── java/com/medicine/app/
│   ├── activities/   # 화면(Activity): 스플래시·로그인·회원가입·메인·OCR
│   ├── adapters/     # RecyclerView 어댑터
│   ├── fragments/    # 탭 화면: 홈·내약·기록·AI·프로필
│   ├── models/       # 데이터 모델 (Medicine 등)
│   ├── receivers/    # BroadcastReceiver (알림 표시·재부팅 알람 복구)
│   ├── utils/        # 알람 스케줄러, 알림 시간 설정 유틸
│   └── views/        # 커스텀 뷰 (촬영 후 크롭 박스)
└── res/
    ├── layout/       # XML 레이아웃
    ├── drawable/     # 아이콘·배경
    └── values/       # 색상·문자열·테마
```

## 참고

- 약 정보·상호작용 안내는 **AI 기반 참고용**이며, 실제 복용 판단은 약사·의사와 상담하세요.
- Gemini는 무료 등급 사용 시 사용량 한도(429)·일시적 과부하(503)가 발생할 수 있어, 여러 모델로 자동 전환하도록 구현되어 있습니다.
