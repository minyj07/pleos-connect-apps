# 배포·출시 (Phase 4)

충전 도우미(앱 + 중계 서버)를 실제로 내보내기 위한 구성과 절차입니다. 코드·설정으로 할 수 있는 것은 준비했고,
운영자가 정하거나 직접 해야 하는 일은 [마지막 표](#운영자가-할-일)에 모았습니다.

## 1. 중계 서버 배포 (Docker + Caddy)

```
인터넷 ──443(TLS 1.2/1.3)──▶ caddy ──8080(내부망)──▶ advisor (ev-advisor-server 이미지)
```

- `server/Dockerfile`: `:server:installDist` 결과만 담는 JRE 21 이미지(비루트 `advisor` 사용자, `-Xmx384m`, 메모리 부족 시 종료 후 재시작).
  Gradle 빌드에 Android 모듈이 함께 있어 이미지 안에서는 빌드하지 않습니다.
- `deploy/docker-compose.yml`: 서버 + Caddy. 서버 포트는 호스트에 열지 않고 Caddy 만 80/443 을 엽니다. 스냅숏은 볼륨에 보관합니다.
- `deploy/Caddyfile`: 자동 HTTPS(Let's Encrypt), TLS 1.2/1.3 만, HSTS, `/v1/*` 만 전달(그 밖은 404). **접근 로그를 켜지 않습니다**
  (`/v1/stations/nearby`·`match` 는 질의 문자열에 좌표가 있음).
- 서버는 `TRUST_PROXY=1` 이면 요청 제한(분당 120회)을 `X-Forwarded-For` 의 마지막 값(Caddy 가 붙인 실제 접속 주소) 기준으로 셉니다.
  이것이 없으면 모든 차가 Caddy 주소 하나로 묶여 함께 제한됩니다.

준비물: 국내 리전 VM 1대(1 vCPU / 1 GB 면 충분), 도메인과 A 레코드, 80/443 개방, Docker.

```powershell
# 개발 PC: 배포본과 이미지를 만들어 VM 으로 옮긴다 (레지스트리 없이)
.\gradlew.bat :server:installDist
docker build -t ev-advisor-server server
docker save ev-advisor-server -o ev-advisor-server.tar   # VM 으로 복사 후: docker load -i ev-advisor-server.tar
```

```bash
# VM: deploy/ 폴더(docker-compose.yml, Caddyfile)를 복사한 뒤
cp advisor.env.example advisor.env   # DATA_GO_KR_SERVICE_KEY 등 채우기 (커밋 금지, *.env 는 gitignore)
ADVISOR_DOMAIN=advisor.example.com docker compose up -d
curl https://advisor.example.com/v1/health
```

- 갱신: 새 이미지를 `docker load` 한 뒤 `docker compose up -d`. 스냅숏 볼륨 덕분에 재시작 직후에도 바로 응답합니다.
- 감시: 외부 업타임 서비스로 `https://<도메인>/v1/health` 를 1~5분마다 확인(`status` 가 `ok`/`degraded` 가 아니면 경보).
- 앱 업데이트 안내: 마켓 심사를 통과한 버전의 `versionCode` 를 `APP_LATEST_VERSION_CODE` 에 넣고 재시작하면,
  그보다 낮은 앱은 시작할 때 업데이트 팝업을 띄웁니다(정차 중에만).
- 로컬 확인(2026-11-03): `ADVISOR_DOMAIN=localhost` 로 띄워 HTTPS 200 + HSTS, `/debug/status-override` 404, HTTP→HTTPS 308,
  openssl 로 TLS 1.1 거절(`alert protocol version`)·1.2/1.3 협상을 확인했습니다.

## 2. 앱 릴리즈 빌드

| 빌드 | 용도 |
| --- | --- |
| `debug` | 개발. 프로브·E2E 도구 포함, 알림 버튼 컴포넌트 adb 노출 |
| `staging` | **release 와 같은 R8·설정**을 debug 키로 서명하고 로컬 서버(`advisor.baseUrl`, cleartext 허용)에 붙여 에뮬레이터에서 확인. 배포 금지 |
| `release` | 배포. HTTPS 서버(`advisor.releaseBaseUrl`)만, 릴리즈 키로 서명, debug 로그 제거 |

서명 키는 운영자가 만들고 보관합니다(분실하면 같은 앱으로 업데이트할 수 없음).

```powershell
keytool -genkeypair -v -keystore C:\keys\ev-companion-release.jks -alias ev-companion -keyalg RSA -keysize 4096 -validity 10000
```

`local.properties`(gitignore)에 적거나 빌드 머신에서는 `RELEASE_STORE_FILE` / `RELEASE_STORE_PASSWORD` / `RELEASE_KEY_ALIAS` /
`RELEASE_KEY_PASSWORD` 환경변수로 줍니다. 키가 없으면 서명되지 않은 APK 가 나옵니다.

```properties
release.storeFile=C:/keys/ev-companion-release.jks
release.storePassword=...
release.keyAlias=ev-companion
release.keyPassword=...
advisor.releaseBaseUrl=https://advisor.example.com
```

```powershell
.\gradlew.bat :app:assembleRelease       # app/build/outputs/apk/release/app-release.apk
.\gradlew.bat :app:assembleStaging       # 에뮬레이터 확인용 (app-staging.apk)
```

- 마켓에 올릴 때마다 `app/build.gradle.kts` 의 `versionCode`·`versionName` 을 올립니다(Pleos 버전 정책).
- R8 규칙(`app/proguard-rules.pro`): Pleos SDK 클래스 유지, **제네릭 시그니처 유지**(Pleos SDK 가 Gson TypeToken 으로 IPC 를 읽음 —
  빠지면 release 빌드에서만 "Vehicle Service 연결 실패 … TypeToken must be created with a type argument", staging E2E 에서 발견해 고침),
  `Log.v/d/i` 제거.

staging 빌드 에뮬레이터 확인(2026-11-03, Docker 서버 컨테이너, FAKE): 대시보드 차량 연결, 업데이트 팝업, CCP Back 숨김(`moveTaskToBack`),
충전 도우미 추천(직렬화·Ktor)·내비 정밀화(NaviHelper)·감시 서비스 모두 정상.

| 성능 항목 | 측정 | Pleos 기준 |
| --- | --- | --- |
| 콜드 스타트(`am start -W`) | 0.9~1.0 s | 10 s 이내 |
| PSS(내비 안내 중, 감시 서비스 실행) | 48.5 MB | (계획 목표 400 MB 이하) |
| CPU(같은 상황, 10 s 간격 6회) | 평균 0.6 %, 최대 3.8 % | (계획 목표 5 % 미만) |
| APK 크기 | 4.9 MB | 600 MB 미만 |

## 3. 심사 요건 대응 (docs/PLEOS_APP_POLICY_SUMMARY.md 기준)

| 요건 | 상태 |
| --- | --- |
| 자동 업데이트 없음 → 앱이 버전 확인·팝업·`market://detail?package=` 이동 | 구현. 문구 "최신 버전이 존재합니다. 업데이트를 위해 페이지를 이동할까요?" (예/아니요), 정차 중에만. 서버 `GET /v1/app-version` |
| CCP Back: 최상단에서 앱을 숨김(`moveTaskToBack(true)`, `finish()` 금지) | 구현. 에뮬레이터에서 호출·프로세스 유지 확인(앱 영역에 다른 태스크가 없어 화면 변화는 없음) |
| CCP 미지원 선언(`com.hmg.rotary.ROTARY_SUPPORTED=false`), 모든 기능 터치로 완결 | 선언 |
| 터치 영역 최소 크기 충족(UX 심사) | Pleos 기본 버튼 높이 100/80/64 dp 적용. 누르는 것은 모두 64 dp 이상, 주행 중 보이는 버튼은 80 dp 이상([ADVISOR_APP.md](ADVISOR_APP.md) "버튼 크기와 위치") |
| 운전과 관련된 알림만, 주행 중 전체 화면 팝업 금지 | 충전 알림만. 업데이트·권한 대화상자는 정차 중에만 |
| 개인정보 동의: 명확한 동의/거부, 동의 버튼 외 동작을 동의로 보지 않음, 철회 시 즉시 중단 | 동의 카드(정차 중 "동의하고 사용"), 설정의 "위치 전송 동의 철회"(서버 호출 즉시 중단, 서버·앱에 저장된 위치 없음) |
| TLS 1.2 이상, 신뢰 서버만, cleartext 금지 | release 는 https 만(빌드 시 검사), main 네트워크 설정 cleartext 금지, Caddy TLS 1.2/1.3 |
| 출시 빌드 디버그 로그 비활성화, 디버그 인증서 금지 | R8 로 `Log.v/d/i` 제거, 위치 로그 없음, 릴리즈 키 서명 |
| 외부 호출 불필요한 컴포넌트 비노출 | release 매니페스트의 exported 는 런처 `MainActivity` 와 AndroidX `ProfileInstallReceiver`(`android.permission.DUMP` 로 보호, 시스템·shell 전용) 둘뿐. 앱 컴포넌트는 모두 `exported=false` (debug 빌드만 E2E 용 노출) |
| 권한 최소화, Vehicle SDK 퍼미션은 사전 승인된 것만 선언 | Pleos Vehicle 권한은 `CAR_ENERGY`·`CAR_DRIVE` 둘. VIN 을 읽지 않으므로 `pleos.car.permission.CAR_INFO` 는 선언하지 않음(2026-11-03 제거) |
| 성능(실행 10 s, 응답 2 s, 과점유 금지) | 위 표 |
| 앱 카테고리 | Mobility Services("전기차 충전소 찾기 및 경유지 설정" 예시). Pleos 문서에 이 카테고리용 매니페스트 선언 값은 없음 |

## 4. 위치정보법 (법률 검토 필요)

조문은 국가법령정보센터 원문으로 다시 확인해야 합니다(아래는 2025-10-01 시행본을 옮긴 페이지에서 읽은 내용, 법률 자문 아님).

- **신고**: 개인위치정보를 대상으로 하는 위치기반서비스사업은 방송미디어통신위원회에 신고(제9조: 상호, 소재지, 사업 종류, 위치정보시스템 등).
  소상공인·1인 창조기업은 신고 없이 시작할 수 있으나, 1개월을 넘겨 계속하려면 시작일부터 1개월 안에 신고(제9조의2).
- **약관·동의**: 위치 이용 전에 이용약관에 사업자 연락처, 권리와 행사 방법, 서비스 내용, 확인자료 보유 근거·기간, 개인위치정보 보유
  목적·기간 등을 적고 동의를 받음(제18·19조). 초안: [docs/legal/location-terms.ko.md](legal/location-terms.ko.md).
- **파기**: 목적 달성 시 즉시 파기(제23조) — 서버는 응답 후 버림.
- **권리**: 동의 철회·일시 중지·열람 요구(제24조) — 앱의 철회·끄기로 대응.
- **확인자료(제16조제2항)**: 위치정보 수집·이용·제공사실 확인자료를 위치정보시스템에 자동 기록·보존해야 합니다. **현재 서버는 위치가 담긴
  요청을 남기지 않으므로** 이 의무가 계정 없는 앱에 어떻게 적용되는지(무엇을, 얼마나) 검토가 필요합니다. 필요하면 좌표 없이 이용 사실만
  (시각·요청 종류) 남기는 기록을 서버에 더하는 것이 방안입니다.
- 개인정보 처리방침 초안: [docs/legal/privacy-policy.ko.md](legal/privacy-policy.ko.md).

출처: [위치정보의 보호 및 이용 등에 관한 법률(현행 2025-10-01, nepla.ai 본문)](https://www.nepla.ai/law/%EC%9C%84%EC%B9%98%EC%A0%95%EB%B3%B4%EC%9D%98%20%EB%B3%B4%ED%98%B8%20%EB%B0%8F%20%EC%9D%B4%EC%9A%A9%20%EB%93%B1%EC%97%90%20%EA%B4%80%ED%95%9C%20%EB%B2%95%EB%A5%A0),
[itwiki 위치정보법 요약](https://itwiki.kr/w/위치정보의_보호_및_이용_등에_관한_법률), 원문: [국가법령정보센터](https://www.law.go.kr).

## 운영자가 할 일

| 항목 | 내용 |
| --- | --- |
| 패키지명 | `com.example.pleosev` 를 실제 이름으로(`app/build.gradle.kts` 의 `applicationId`). 마켓에 올린 뒤에는 바꿀 수 없음 |
| 서명 키 | 위 `keytool` 로 만들고 안전하게 보관, `local.properties` 에 연결 |
| 서버 | VM·도메인·DNS 준비 후 1장대로 배포, `/v1/health` 외부 감시 등록 |
| data.go.kr | 개발계정 키로 실데이터 확인 완료(2026-11-04, spike 0-4). 출시 전 운영계정(트래픽 증가)을 신청하고 그 키를 `advisor.env` 에 넣기. 실측 기준 하루 약 1천~2천 회(전체 동기화 1회 약 75회 포함) |
| Pleos App Market | 개발자 등록, Vehicle SDK 권한(`pleos.car.permission.CAR_ENERGY`·`CAR_DRIVE`) 사전 협의·승인과 프로젝트 ID, 카테고리 Mobility Services, IARC 등급, 아이콘(512×512)·스크린샷(2장 이상, 2560×1380 등), 고객 지원 연락처, 심사 노트(포그라운드 서비스 사유, 서버 운영 중 확인) |
| 개인정보·위치 | 처리방침·위치약관 초안 검토 후 게시(URL), 위치기반서비스사업 신고(또는 소상공인 1개월 내 신고), 확인자료 기록 여부 결정 |
| Pleos 기술지원 문의 | 비미디어 `specialUse` 포그라운드 서비스 허용, 서드파티 헤드업 알림 표시 조건(2026-11-03 에뮬레이터 미표시), 외부 충전소 ID(`poiProvider`). 실차 테스트는 공모전 설명회(2026-10-28)에서 "실차 시연 미지원, 에뮬레이터로 검증"이라고 답했다([SPIKE §4](SPIKE_NAVI_EV.md)). 문의처 appdevchallenge@hyundai-partners.com |
