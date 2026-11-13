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
> - e2e 토큰은 `~/.config/notify-e2e/token`(권한 600)에만 둡니다.

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

## 5. ntfy 서버 설정 (적용됨: homelab-gitops#188)

ntfy는 ArgoCD + SealedSecrets로 배포됩니다(`homelab-gitops/workloads/ntfy/`). `kubectl`로 직접 바꾸면 ArgoCD가 되돌리므로 gitops에 커밋합니다.

1. 4번 키를 봉인합니다. 평문은 커밋하지 않습니다.
   ```sh
   kubectl create secret generic ntfy-firebase -n ntfy \
     --from-file=firebase-key.json=<받은 키>.json --dry-run=client -o yaml \
   | kubeseal --format yaml --controller-name sealed-secrets --controller-namespace sealed-secrets \
     > workloads/ntfy/sealedsecret-firebase.yaml
   ```
2. `kustomization.yaml` resources에 `sealedsecret-firebase.yaml`을 추가합니다.
3. `deployment.yaml`: Secret `ntfy-firebase`를 `/etc/ntfy-firebase`에 읽기 전용으로 마운트합니다.
   - `/etc/ntfy`는 server.yml ConfigMap 마운트라서 그 아래에는 둘 수 없습니다.
4. `server.yml`:
   ```yaml
   firebase-key-file: /etc/ntfy-firebase/firebase-key.json
   ```
   `base-url`, `auth-default-access: deny-all`은 이미 설정돼 있습니다. 모든 토픽이 deny-all이라 FCM에는 본문 대신 poll_request만 갑니다.
5. 머지 후 ArgoCD sync → 파드 재시작(Recreate). 로그에 Firebase 오류가 없는지 확인하고, 내려받은 키 파일은 지웁니다.

키 교체: Firebase 콘솔에서 새 키 → 다시 봉인 → 콘솔에서 옛 키 삭제.

## 6. 앱에서 할 일

1. 앱 설치: play 플레이버만 FCM을 씁니다. GitHub Releases APK가 play 플레이버입니다.
2. **설정 → 사용자 관리**에서 `https://ntfy.eli.kr`용 사용자/토큰을 추가합니다.
   - poll_request를 받은 뒤 이 토큰으로 메시지를 가져옵니다.
3. 토픽 구독: 기본 서버가 ntfy.eli.kr입니다.
   - 이 서버의 토픽만 FCM 토픽으로 등록됩니다.

## 7. 확인 방법

1. 앱에서 `e2e-fcm` 토픽을 구독합니다("즉시 알림"은 끔 → FCM만으로 받는지 확인).
2. 앱을 최근 앱에서 밀어 종료하고 배터리 절약 모드를 켭니다.
   - 설정의 "강제 종료"는 쓰지 마세요. 강제 종료된 앱에는 FCM도 전달되지 않습니다(Android 규칙).
3. PC에서 보냅니다. 토큰은 파일에서 읽고 화면이나 기록에 남기지 않습니다.
   ```sh
   curl -H "Authorization: Bearer $(cat ~/.config/notify-e2e/token)" -d "fcm test" https://ntfy.eli.kr/e2e-fcm
   ```
4. 몇 초 안에 알림이 오면 성공입니다.

2026-11-13 확인: S25+(Android 17), 앱 종료 + 배터리 절약 모드에서 전송 후 약 2초 만에 알림.
- poll_request를 받으면 `PollWorker`를 expedited로 예약합니다. 일반 작업으로 두면 배터리 절약 모드에서 최대 약 30분까지 미뤄집니다(실측: 60초 넘게 안 옴).
