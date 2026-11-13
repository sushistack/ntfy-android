# Notify FCM 설정 가이드

Notify(`kr.eli.notify`)는 내 Firebase 프로젝트로 FCM을 받습니다.
흐름은 poll_request 방식입니다.
1. 서버(https://ntfy.eli.kr)가 FCM으로 "새 메시지 있음" 신호만 보냅니다.
2. 앱이 깨어나 저장된 토큰으로 서버에서 메시지를 직접 가져옵니다.

메시지 내용은 Google을 거치지 않습니다. 모든 토픽이 인증 필수라서, ntfy 서버는 FCM에 본문 대신 poll_request를 보냅니다.

> 비밀값 규칙
> - `google-services.json`, 서비스 계정 키, 토큰은 레포에 커밋하지 않습니다.
> - `app/google-services.json`은 `.gitignore`에 들어 있습니다.
> - CI에서는 GitHub Actions secret으로만 넣습니다(6단계).

## 1. Firebase 프로젝트 만들기

1. https://console.firebase.google.com 에서 **프로젝트 추가**를 누릅니다.
   - 이름은 예를 들어 `notify`로 합니다.
   - Google Analytics는 **사용 안 함**으로 해도 됩니다(FCM에는 필요 없음).
2. 만든 프로젝트로 들어갑니다.

## 2. Android 앱 2개 등록 (릴리스 + 디버그)

**프로젝트 설정(⚙) → 일반 → 내 앱 → Android 앱 추가**를 두 번 합니다.

| 패키지 이름 | 닉네임 | 용도 |
|---|---|---|
| `kr.eli.notify` | Notify | 릴리스 APK (GitHub Releases) |
| `kr.eli.notify.debug` | Notify debug | 개발용 디버그 빌드 |

- SHA-1은 FCM에 필요 없으니 비워 둬도 됩니다.
- "구성 파일 다운로드" 단계는 건너뛰어도 됩니다. 두 앱을 모두 등록한 다음 3번에서 받습니다.
- SDK 추가 안내 단계도 건너뜁니다. 코드에 이미 들어 있습니다.

## 3. `google-services.json` 받기 (앱용)

1. **프로젝트 설정 → 일반 → 내 앱**에서 아무 Android 앱이나 고르고 `google-services.json`을 다운로드합니다.
   - 두 앱을 다 등록한 뒤 받으면 파일 하나에 `client`가 2개 들어 있습니다.
2. 로컬 빌드: 파일을 `app/google-services.json`에 둡니다. git에는 잡히지 않습니다.
3. CI(6단계): `base64 -w0 google-services.json` 결과를 GitHub 레포의 secret `GOOGLE_SERVICES_JSON_B64`로 등록합니다.

이 파일의 API 키는 앱에 들어가는 공개 식별자에 가깝습니다. 그래도 이번 작업 규칙에 따라 레포에는 넣지 않습니다.

## 4. 서버용 서비스 계정 키 받기 (비밀)

1. **프로젝트 설정 → 서비스 계정 → Firebase Admin SDK → 새 비공개 키 생성**을 누릅니다.
2. JSON 파일이 내려받아집니다. 이 파일은 **진짜 비밀**입니다.
   - 레포, 채팅, 로그 어디에도 붙여 넣지 마세요.
   - 서버의 시크릿으로만 보관합니다(예: k8s Secret).

## 5. ntfy 서버 설정값 (참고용 — gitops는 이 작업에서 건드리지 않음)

ntfy 서버 `server.yml`에 필요한 값입니다. 적용은 직접 하시면 됩니다.

```yaml
base-url: "https://ntfy.eli.kr"
# 4번의 서비스 계정 키를 Secret으로 마운트한 경로
firebase-key-file: "/etc/ntfy/firebase-key.json"
# 이미 모든 토픽이 인증 필수라면 그대로 둡니다. 익명 읽기가 안 되는 토픽은
# FCM으로 본문 대신 poll_request만 갑니다(메시지 내용이 Google을 거치지 않음).
auth-default-access: "deny-all"
# upstream-base-url 은 iOS(ntfy.sh 중계)용이라 Android에는 필요 없습니다.
```

- 적용 후 서버 로그에 Firebase 관련 오류가 없는지 확인합니다.
- `firebase-key-file`이 없으면 FCM 전송이 꺼집니다. 이때 앱은 계속 연결(instant delivery)로만 받습니다.

## 6. 앱에서 할 일

1. 앱 설치: play 플레이버만 FCM을 씁니다. GitHub Releases APK가 play 플레이버입니다.
2. **설정 → 사용자 관리**에서 `https://ntfy.eli.kr`용 사용자/토큰을 추가합니다.
   - poll_request를 받은 뒤 이 토큰으로 메시지를 가져옵니다.
3. 토픽 구독: 기본 서버가 ntfy.eli.kr입니다.
   - 이 서버의 토픽만 FCM 토픽으로 등록됩니다.

## 7. 확인 방법

1. 앱에서 `e2e-fcm` 토픽을 구독합니다.
2. 앱을 완전히 종료(최근 앱에서 밀기)하고 배터리 절약 모드를 켭니다.
3. PC에서 보냅니다. 토큰은 셸 변수로만 쓰고, 기록에 남기지 않습니다.
   ```sh
   read -rs NTFY_TOKEN
   curl -H "Authorization: Bearer $NTFY_TOKEN" -d "fcm test" https://ntfy.eli.kr/e2e-fcm
   ```
4. 몇 초 안에 알림이 오면 성공입니다.
