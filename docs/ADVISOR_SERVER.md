# 충전 도우미 중계 서버 (`:server`)

한국환경공단 전기자동차 충전소 API(data.go.kr 15076352, `B552584/EvCharger`)를 받아 메모리에 캐시하고,
차량 앱에 충전소 추천·상태를 JSON으로 제공하는 Ktor 서버입니다. API DTO·점수 계산·이름 매칭·방향 판별은
앱과 함께 쓰는 `:shared` 모듈에 있습니다.

## 실행

```powershell
# 합성 데이터(FAKE) 모드: 키 없이, data.go.kr 호출 없이 실행
$env:FAKE_SOURCE = "1"; .\gradlew.bat :server:run

# 실제 API: deploy\advisor.env 에 data.go.kr "Decoding" 키를 넣고(DATA_GO_KR_SERVICE_KEY=<Decoding 키>)
# 불러온 뒤 실행. Encoding 키는 이중 인코딩되어 SERVICE_KEY_IS_NOT_REGISTERED.
.\scripts\advisor-env.ps1; .\gradlew.bat :server:run
```

- `deploy\advisor.env`는 Docker 배포(`env_file`)와 같은 파일이고 `*.env`로 gitignore 됩니다. `scripts\advisor-env.ps1`은 값을 출력하지 않고, 이미 설정된 환경변수는 덮어쓰지 않습니다(`$env:DATA_GO_KR_SERVICE_KEY`로 직접 줘도 됨).

- 기본 바인딩 `127.0.0.1:8080`, 스냅숏은 `server/data/snapshot.json.gz`(gitignore). FAKE 모드는 스냅숏을 읽지도 쓰지도 않습니다(`/debug/status-override` 로 바꾼 상태가 다음 실행에 남지 않도록).
- 백그라운드로 띄울 때는 배포본을 만든 뒤 `java`로 직접 실행합니다. 경로의 쉼표 때문에 `bin\server.bat`(cmd)은 쓸 수 없습니다.
  ```powershell
  .\gradlew.bat :server:installDist
  .\scripts\advisor-env.ps1   # 실제 API 일 때
  & "D:\Android Studio\jbr\bin\java.exe" -Xmx384m -cp "C:\dev\hyundai,kia\server\build\install\server\lib\*" com.example.pleosev.server.ApplicationKt
  ```
  `-cp '"…"'`처럼 따옴표를 겹치면 PowerShell 7(`$PSNativeCommandArgumentPassing = Windows`)에서 ClassNotFoundException 이 납니다.
- 에뮬레이터에서 접속: `adb reverse tcp:8080 tcp:8080` 후 앱에서 `http://127.0.0.1:8080` (또는 `http://10.0.2.2:8080`). 2026-11-03 Pleos 에뮬레이터에서 `/v1/health` 응답 확인.

| 환경변수 | 기본값 | 설명 |
| --- | --- | --- |
| `DATA_GO_KR_SERVICE_KEY` | — | data.go.kr Decoding 키. `FAKE_SOURCE`가 아니면 필수 |
| `FAKE_SOURCE` | 꺼짐 | `1`이면 `server/src/main/resources/fake/stations.json`(합성 충전소 24곳) 사용. 괴산(WTFK0201)·문경(HDFK0204) 좌표는 에뮬레이터 내비의 충전소 POI 위치와 같게 맞춰 경유지 매칭을 E2E 로 확인할 수 있습니다 |
| `HOST` / `PORT` | `127.0.0.1` / `8080` | |
| `DATA_DIR` | `data` | 스냅숏 위치(`:server:run`은 작업 디렉터리가 `server/`) |
| `POLL_INTERVAL_SEC` | `90` | 상태 변경분 조회 주기 |
| `DAILY_CALL_BUDGET` | `10000` | 개발계정 일일 호출 한도 |
| `STATUS_PER_ZCODE` | 꺼짐 | 시도별로 상태를 조회합니다. 0-4 실측에서 zcode 없이 전국 조회가 돼 필요 없음 |
| `TRUST_PROXY` | 꺼짐(이미지에서는 켬) | 리버스 프록시(Caddy) 뒤: 요청 제한을 `X-Forwarded-For` 마지막 값 기준으로. 프록시 없이 켜면 클라이언트가 값을 꾸밀 수 있음 |
| `APP_LATEST_VERSION_CODE` / `_NAME` | 없음 | 마켓 최신 앱 버전. 앱이 이보다 낮으면 업데이트 팝업(`GET /v1/app-version`) |

운영 배포(Docker 이미지 + Caddy 자동 HTTPS)는 [DEPLOY.md](DEPLOY.md).

## API (`/v1`, JSON, gzip, `Cache-Control: no-store`)

| 엔드포인트 | 내용 |
| --- | --- |
| `GET /health` | `status`(starting / ok / degraded), 충전소·충전기 수, 마지막 전체/상태 동기화, 오늘 호출 수 |
| `GET /app-version` | `latestVersionCode`·`latestVersionName`(설정 안 하면 null). 앱 시작 시 업데이트 확인용, 요청에 개인정보 없음 |
| `POST /recommendations` | `RecommendationRequest` → `RecommendationResponse`. 모드 `ON_ROUTE` / `NEARBY` / `AROUND_PLANNED_STOP` / `NEAR_DESTINATION` |
| `GET /stations/nearby?lat&lon[&radiusKm&minKw&connectors&headingDeg&limit]` | 주변 DC 충전소(가까운 순, 진행 방향 ±90° 우선). 이용 제한·고장 충전소도 상태와 함께 표시 |
| `POST /stations/status` | `{statIds(≤20), prefs}` → 요약 목록과 없는 id. 위치를 보내지 않는 주기 갱신용 |
| `GET /stations/{statId}` | 충전기별 상태(`ChargerDto`), 이용 제한 사유, 연락처 |
| `GET /stations/match?lat&lon[&name&kw]` | 내비 충전소(좌표·이름·kW) → statId. `best`(신뢰도 ≥ 0.6), `alternatives`, 같은 장소 다른 운영사 `siteStatIds` |
| `POST /debug/status-override` | FAKE 모드 전용, 경로는 `/v1` 밖(`http://127.0.0.1:8080/debug/status-override`). `{"statId","chgerId"?,"stat"}`로 충전기 상태를 바꿔 만차 등을 재현(`"stat":"3"` = 충전 중). 다음 상태 폴링(최대 90 s) 뒤 반영 |

오류는 `{"error":{"code","message"}}`: 400 `INVALID_COORDINATE`·`MISSING_PARAMETER`·`MISSING_DESTINATION`·`BAD_REQUEST` 등,
404 `NOT_FOUND`, 429 `RATE_LIMITED`(접속 주소당 분당 120회, 프록시 뒤에서는 `TRUST_PROXY`), 503 `NOT_READY`(+ `Retry-After: 30`, 첫 동기화 전).

## 동기화

- **전체(getChargerInfo)**: 시작 시 스냅숏이 없거나 그 충전소 목록이 하루보다 오래됐을 때 + 매일 04:00 KST. 시도 코드 17개(강원·전북은 특별자치도 코드 51·52만. 옛 42는 503, 45는 0행) × 9,999행 페이지. DC 충전기만 남기고(AC·삭제·좌표 없음 제외) 새 인덱스로 통째 교체합니다. 직전보다 10% 넘게 줄면 교체하지 않습니다. 실패하면 15분 뒤 다음 상태 조회 때 다시 시도합니다(충전소가 하나도 없을 때 포함. 2026-11-05 전에는 첫 동기화가 실패하면 다음 날 04:00까지 503이었음).
  - 재시작(2026-11-05): 스냅숏에 마지막 상태 조회 시각도 넣어, 다시 켜면 그 시각부터 상태 조회로 이어 갑니다. 공백이 10분 안이면 조회 한 번(1~2회 호출)으로 따라잡고, 넘으면 전체 동기화합니다. 전에는 재시작할 때마다 전체 동기화(60~80회 호출, 약 11분 동안 모든 충전소 STALE)를 했습니다. 스냅숏은 따라잡기 조회 뒤에 저장하고(저장 실패가 동기화 실패가 되지 않게), 저장끼리 겹치지 않으며, 스트림으로 읽고 씁니다(약 19 MB JSON).
  - 실제 데이터(2026-11-04): 59페이지, DC 충전소 21,740곳·충전기 48,477대, 약 11분. 게이트웨이 시간 초과(30초 뒤 504)가 호출 다섯 번에 한 번꼴이라, 호출마다 5번까지 재시도하고(2·4·6·8초 간격) 재시도마다 키 없이 경고 로그를 남깁니다.
  - 동기화가 상태 변경분 창(최대 10분)보다 길면, 끝난 즉시 따라잡기 조회를 하고 연결은 9분 전부터 다시 시작합니다. 동기화 처음 몇 분 사이의 변경은 다시 확인하지 않고, 그 충전기의 다음 상태 변경 때 반영됩니다. 전에는 이 공백 때문에 전체 동기화가 끝없이 반복됐습니다.
- **상태 변경분(getChargerStatus)**: 90초마다 `period=5`. 직전 성공 이후 공백이 4분 30초를 넘으면 `period=10`, 9분 30초를 넘으면 연속성이 깨진 것으로 보고 모든 충전소를 `STALE`로 표시한 뒤 전체 동기화를 합니다. 상태는 `statUpdDt`가 같거나 새로울 때만 덮어씁니다. 미래 시각이 찍힌 `statUpdDt`는 받은 시각으로 봅니다(그대로 두면 그 시각까지 다음 변경을 모두 막음).
- **오래된 "충전 중"**: 세션이 3시간을 넘으면(KECO `nowTsdt` 기준, 없으면 `statUpdDt`) 상태 불명으로 셉니다. 운영사가 상태가 그대로여도 `statUpdDt`를 새로 찍어서, 전에는 하루 넘게 "충전 중"인 충전기도 바쁜 것으로 셌습니다(실데이터 스냅숏에서 62대).
- **신선도**: 충전기의 `statUpdDt`가 아니라 서버가 마지막으로 확인한 시각 기준(3분 이내 LIVE, 15분 이내 DELAYED). 하루 종일 비어 있는 충전기는 `statUpdDt`가 오래된 것이 정상이기 때문입니다.
- **호출 한도**: KST 자정에 초기화. 95% 이상이면 폴링 주기 2배, 100%이거나 API가 22(한도 초과)를 주면 그날은 호출을 멈춥니다. 예상 사용량은 하루 약 1천~2천 회입니다(상태 조회 1~2페이지 × 하루 960회 + 전체 동기화 60~80회).
- **키 보호**: 키는 환경변수로만 받습니다(로컬에서는 `deploy\advisor.env`를 `scripts\advisor-env.ps1`로 불러옴). 예외 메시지에서 키를 지우고, 요청 URL·바디(위치 포함)는 로그에 남기지 않습니다.

## 추천 로직 (서버 1차 계산)

내비가 경로 선을 주지 않으므로, 출발지 → 경유지 → 목적지를 직선 구간으로 보고 내비의 남은 거리로 굴곡률을 보정합니다.
앱이 상위 후보를 NaviHelper `requestTrip`(실제 도로 거리·ETA·도착 SOC)으로 다시 계산해 같은 `StationScorer`로 순위를 다시 매깁니다([ADVISOR_APP.md](ADVISOR_APP.md)).

1. **배터리 모델**: 소비율(%/km) = (SOC − 내비 도착 SOC) ÷ 남은 거리 → 안 되면 SOC ÷ 주행가능 거리 → 기본 0.22. 도달 거리 = (SOC − 예비 SOC) ÷ 소비율.
2. **선호 구간**: 도달 거리의 45~85% (남은 거리 − 10 km 이내). 이 구간에 조건을 모두 통과한 후보(출력·커넥터·운영 중·우회 등)가 3곳 이상이면 구간 시작의 절반보다 앞선(출발 직후) 충전소는 뺍니다. 2026-11-05 전에는 조건을 보기 전에 세어서, 예를 들어 "200 kW+"에서 구간에 50 kW 충전소만 있어도 앞쪽의 200 kW 충전소를 뺐습니다(실데이터 7,620개 도시 쌍 중 84개 경로).
3. **경로 위 후보**: 우회 거리(타원) ≤ max(10 km, 직선 거리의 12%). 출발 뒤쪽·목적지 너머는 제외합니다. 이름의 "(○○방향)"이 진행 방향과 100°보다 크게 어긋나면 반대편 차로 휴게소로 보고 제외합니다(`DirectionHints`). 실데이터(2026-11-04)의 방향 표기 183곳을 보고 포천·화도·일산 등 10곳을 표에 더했고, "음성제천방향"처럼 붙은 두 도시는 앞뒤 두 글자로 찾습니다.
4. **제외**: 호환 충전기 없음(기본 DC콤보 50 kW 이상), 전부 운영중지·점검, 이용 제한(설정 시 포함), 도착 SOC 3% 미만(긴급 0.5%), 우회 30분 초과(긴급 제외), 앱이 제외한 id.
5. **점수**(`:shared` `StationScorer`): 가용 35 · 출력 15 · 우회 25 · 도착 여유 15 · 선호 구간 10. 신선도 배수(LIVE 1.0 / DELAYED 0.9 / STALE 0.7)와 운영사 선호(+5) / 회피(−15)를 적용하고, 같은 20 km 구간에서 최대 2곳만 남깁니다.
6. **내비 계획 충전소 대안**(`AROUND_PLANNED_STOP`): 계획 충전소를 매칭해 현재 상태를 `plannedStop`으로 돌려주고, 그 주변 30 km 안에서 대안을 고릅니다. 목적지 없이 오면 우회 시간을 차가 아니라 계획 충전소에서 잽니다(대안은 그 충전소를 대신하므로).

합성 데이터 확인(2026-11-03): 판교 → 부산역, SOC 42%, 210 km 기준 선호 구간 72~136 km. 내비가 고른 괴산(워터)을 만차로 바꾸면 대안으로 같은 휴게소 환경부 충전소, 충주, 문경이 나오고, 반대편 괴산(양평방향)은 제외됩니다.

## 실제 API 실측 (spike 0-4, 2026-11-04)

자세한 표는 [SPIKE_NAVI_EV.md §8](SPIKE_NAVI_EV.md). 서버에 주는 영향만 적습니다.

- 응답은 평면형 JSON(값은 모두 문자열), 최소 10행. 서버가 쓰는 필드는 모두 있습니다.
- 상태 조회는 zcode 없이 전국이 됩니다. 5분 창이 약 9,800행이라 붐비는 시간에는 2페이지가 됩니다.
- 시도 코드는 17개(강원 51·전북 52). 시군구(`zscode`) 조건은 504로 쓸 수 없습니다.
- 게이트웨이 오류는 JSON 봉투(`OpenAPI_ServiceResponse`)와 HTTP 오류 상태로 옵니다(잘못된 키 403 + 30, 시간 초과 504 + 05). 파서가 이 코드를 그대로 씁니다.
- 아직 못 본 것: `chgerType` 08~10의 의미, 늦게 보고된 변경이 period 창에서 빠지는지.

## 알려진 한계

- 직선 기하라 굽은 경로에서는 우회·도로 거리가 부정확합니다(앱의 `requestTrip` 정밀화로 보완). 경로 위 휴게소를 더 정확히 고르려면 앱이 내비 TBT 목록의 휴게소 이름을 힌트로 보내는 방안을 Phase 2에서 검토합니다.
- 방향 표기가 없는 휴게소(예: "(하행)")는 상·하행을 구분하지 못합니다.
- 위치 정보: 서버는 요청 좌표를 저장·기록하지 않습니다. 위치기반서비스사업 신고와 위치정보 이용사실 확인자료(제16조제2항) 기록 여부는
  [DEPLOY.md §4](DEPLOY.md) 의 법률 검토 항목입니다.
