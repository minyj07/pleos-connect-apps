# Pleos Connect 주변 문서 요약 — 등록·심사·플랫폼 정책

> 대상: Pleos Connect(AAOS 기반) SDK로 EV 인포테인먼트 앱을 개발하는 개발자. SDK 가이드 본문 외의 등록/심사/정책 문서 26건을 요약한다. 수치·식별자는 원문 표기 그대로 유지했다.

---

## (a) 앱 등록 / 제출 / 심사

출처: `distribute/app-submission-guide.md`, `distribute/app-review-guide.md`, `distribute/app-review-guide/review-app-registration-info.md`, `review-content-safety.md`, `review-driver-distraction-elements.md`, `review-functionality-performance-stability.md`, `review-user-privacy-security.md`

### a-1. 심사 제출 전 준비 (app-submission-guide.md)

- **개발자 정보 최신화**: 심사팀이 언제든 연락 가능한 연락처 필수.
- **기능 테스트**: 실행 불가, 크래시, 성능 저하, 버그, 이미지 깨짐·해상도 늘어짐 확인. 과도한 성능 점유(과부하, 높은 배터리 소모)는 차량 성능·안전과 직결 → 「성능 및 리소스 효율을 고려한 설계」 문서의 메모리·CPU·배터리 항목 참조.
- **보안성**: 취약점·멀웨어·바이러스·악성 코드 여부 확인 → 「앱 보안 가이드」 참조.
- **메타데이터**: 앱 명칭/버전명/카테고리, 출시 국가/연령 등급/접근 권한/개인정보 처리 여부, 패키지·아이콘·스크린샷, 이용약관·개인정보 처리방침.
- **심사용 계정**: 모든 기능·콘텐츠 접근이 활성화된 계정 제공. 외부 서버 연동 앱은 제출 전 운영 구성 요소를 미리 활성화.
- **운전자 주의 분산 방지 설계** → 「주행 안전을 고려한 설계」 문서.
- **출시 국가 법률·규제 준수**: 식약품·의료기기·서비스 등 판매/홍보 품목의 허용 여부 확인, 규제 허용 증빙 자료 제출 요구.
- **연령 등급**: Pleos App Market은 IARC(국제등급분류연합)와 연계 → IARC 심의를 거쳐 등급 분류를 받고 증적을 함께 제출. 설정 등급보다 높은 수위의 콘텐츠 확인 시 심사 거부. 게임 등 일부 콘텐츠는 국가별 별도 심의(예: 한국 게임물 관리 위원회).
- **심사 노트**: 설명이 필요한 기능 주석 및 기능 사용 설명서 첨부.

### a-2. 심사 제출 항목 (Pleos App Market Console: `https://appmarket.pleos.ai/console/`)

| 카테고리 | 항목 | 필수 | 규격/설명 |
| --- | --- | --- | --- |
| 앱 식별 | 앱 관리명 / 버전명 | 필수 | 마켓 비노출 관리용 |
| 기본 정보 | 국가/지역 설정 | 필수 | 출시 국가/지역 선택 |
| | 패키지 파일 | 필수 | APK 형식, 최대 600 MB |
| | 대상 플랫폼 | 필수 | Connect 또는 InfoLINK |
| | 카테고리 / 연령 등급 | 필수 | |
| | 개인정보 처리 | 필수 | 처리(수집,이용,파기,생성,연계,전송 등)하는 모든 개인정보 선택/입력 |
| | 위치정보 활용 | 필수 | GPS, 지도, 사용자 위치 등 활용 시 선택 |
| | 앱 접근 권한 | 필수 | |
| | **SDK 추가 권한** | 필수 | 권한 신청이 필요한 SDK 사용 앱은 My Project > 프로젝트 정보 > 프로젝트 ID 입력 |
| | 광고성 정보 | 필수 | 푸시 알림/SMS 광고 전송 여부 |
| 언어별 정보 | 앱 이름, 간단한 설명, 자세한 설명 | 필수 | 출시 국가/지역별 모든 언어로 작성 |
| | 개인정보처리방침 URL | 필수 | 국가/지역별 언어 모두 확인 가능해야 함 |
| | 위치정보 서비스 이용약관 및 기타 URL | 조건부 필수 | 기본 정보에서 '위치정보'/'GPS' 선택 또는 위치정보 활용 '활용함' 선택 시 필수 |
| | 태그 | 선택 | 최대 20개 |
| | 앱 아이콘 | 필수 | PNG 또는 JPEG, 최대 1 MB, 512 X 512 px |
| | IVI 스크린샷 | 필수 | 1장당 최대 8MB, PNG/JPEG, 최대 8개. 규격 2560 x 1380 px, 1700 x 1212 px, 840 x 121 px (등록정보 문서에는 `840 xX 1212 px`로 표기되어 두 문서가 다름) |
| | 출시 노트 | 필수 | |
| 고객 지원 | 웹사이트 / 이메일 / 전화번호 | 1개 이상 필수 | 국가/지역별 언어 확인 가능 |
| 심사 요청 | 심사 노트 | 선택 | 최대 60 MB 파일 첨부 가능 |

### a-3. 심사 통과 후 관리

- 마켓은 주기적으로 앱을 샘플링 모니터링하여 재심사·필터링. 심각한 오류·손상 보고, 장기간 미업데이트 방치, 다수 신고 앱은 재심사 대상. 기준 미달 시 마켓에서 제외.

### a-4. App Review Guidelines 총론 (app-review-guide.md)

- 심사는 **운전자 주의 분산 요소**(안전 위협·위험 행동 유도 여부)와 **사이버보안 위협 요소**를 중점 검증.
- 가이드 전반 준수가 위험 요소의 완전 배제를 의미하지 않음 → 심사 단계에서 추가 위험 판단 시 거부 가능.
- 5개 영역: 콘텐츠 안전성, 앱 기능 및 성능 안정성, 운전자 주의 분산 최소화 설계, Pleos App Market 등록정보, 사용자 개인정보 및 보안.
- SDK 악용 또는 사용자 데이터 무단 활용은 엄격히 금지.

### a-5. 콘텐츠 안전성 (review-content-safety.md)

- 미디어 콘텐츠 앱은 서비스 대상 국가/지역 심의 필수. OTT·웹툰 등 중계 플랫폼 또는 UGC 앱은 **부적절 콘텐츠 필터링 기능 + 신고 채널** 필수. 공익·ESDA(교육, 과학, 다큐멘터리, 예술) 목적은 허가 가능.
- 금지 콘텐츠: 안전 저해(음주 운전, 과속, 신호 위반 권유·조장·홍보·미화), 선정적, 폭력적(과도한 욕설·폭언 포함), 위험(자살·자해 조장, 아동이 차량 밖으로 신체를 내밀도록 유도), 불법(테러·약물·무기·인신매매), 사행성(실제 금전 도박), 차별, 민감(재난·사고·전쟁·전염병으로 이익 취득), 승인되지 않은 물품(처방전 없는 처방약 등).
- 주류 광고 시 국가별 광고법 준수 + 음주 운전 위험성 경고 포함 필수.
- 미성년자 보호: 별도 인증 없이 볼 수 있는 화면·미리보기·썸네일·인앱 스크린샷·상품 사진에는 '전체 이용가' 등급 콘텐츠만 노출.
- 연령 등급은 등급 조사 설문 답변으로 부여 → 정직 답변. 등급에 부적절한 콘텐츠 확인 시 제한·신고.
- 지적 재산권 침해 신고 시 다운로드 경로 우선 차단, 기간 내 법적 정당성 미소명 시 앱/개발자 영구 제외.
- UGC·AI 생성 콘텐츠 사용 앱: 필터링 + 개발자에게 알리는 신고 기능 필수.
- **암호화폐 채굴 앱은 거부**(하드웨어 손상·성능 저하).
- 루트 박스·무작위 가상 아이템: 구입 전 각 항목 유형의 수령 확률 공개.

### a-6. 앱 기능 및 성능 안정성 (review-functionality-performance-stability.md)

- 설치/삭제 오류 금지(예: 인증 키 누락으로 설치 불가, 파일 누락, 삭제 후 아이콘·파일 잔존). 실행 시 강제 종료·하드웨어 재시작 금지. 화면 멈춤, 기능 미동작, 터치 미인식 금지. 사운드 끊김·그래픽 깨짐 금지.
- 제출 앱 = 실제 서비스 앱. 테스트용 더미 데이터·임시 파일·임시 콘텐츠 금지.
- UI 왜곡·흐림·모자이크 금지. 디스플레이 컷아웃, 끝면 인셋, 가장자리 디스플레이, 소프트웨어 키보드, 시스템 표시줄 포함 시 **기기 안전 영역** 준수.
- 카테고리를 벗어나는 기능 포함 또는 서비스와 상이한 유형 선택 시 거부 가능.
- **성능 수치**:
  - 입력 후 차량 시스템 응답 시간 **0.25초** 이내(터치 → 터치 효과 애니메이션 발생까지).
  - 앱 실행 **10초** 이내, 콘텐츠 로드 **10초** 이내, 앱 내 특정 버튼 **2초** 이내 반응.
  - 2초 내 버튼 동작·콘텐츠 로드 불가 시 UI로 동작 중임을 표시.
  - 스터터링, 프레임 저하, 화면 끊김 등 심각한 성능 저하 금지. 앱 점유로 인한 시스템 성능 저하 금지.
- 백그라운드 복귀 시 마지막 상태 복원(수 시간 이상 미사용 시에만 기본 상태 허용).
- 하드웨어/소프트웨어 손상 및 과점유 금지. 사용자에게 기기 재시작이나 핵심 기능과 무관한 시스템 설정 변경 요구 금지.
- **SDK·API 사용 심사 — Vehicle SDK 퍼미션 기준**:
  - Vehicle SDK API 사용 시 해당 API에 필요한 퍼미션을 앱에 선언해야 함.
  - Vehicle SDK 퍼미션은 **Pleos App Market과 사전 협의 및 승인된 경우에만** 선언 가능.
  - 승인되지 않은 퍼미션을 선언한 앱은 심사 통과 불가.
- **디스플레이 호환성**: Display Layout 가이드 준수. 레터박스 없이 디스플레이 최대 채움(미세 비율 보정용 한 방향 레터박스는 허용 가능). 호환성 모드 실행 금지. 해상도 전환 시 상태 손실·이미지 늘어짐·앨리어싱·블러·터치 영역 불일치 금지. 모든 화면 상태에서 동일 기능 노출(기능 패리티).

### a-7. 운전자 주의 분산 요소 최소화 (review-driver-distraction-elements.md)

- Pleos Connect UI Design Guide 사전 확인 필수.
- **미디어 앱(동영상, 게임, 브라우저)**: 운전 중 실행·사용 불가, 정차 중에만 사용. 운전 시작 시 일시 중지 + UI 숨김. 운전 중 일시 중지 해제 불가.
- 백그라운드 복귀 시 상태 복원(수 분 이내 복귀: 최대한 가깝게 복원, 수 시간 이상: 기본 상태 가능).
- 모든 미디어 항목에 적절한 제목·썸네일, 부적절 내용 금지.
- **미디어 앱은 헤드업 알림 게시 금지. 운전과 관련되지 않은 알림 사용 불가.**
- 동영상 앱은 미디어 세션 통합, 재생/일시정지/중지 지원, 다중 미디어 동시 사용 불가.
- 브라우저 앱: 비밀번호·결제 정보 저장/액세스 금지(프로필 잠금으로 차단 가능한 경우 예외). 동기화 시 인증 메시지 표시 + 차량 화면에서 동기화 사실 고지.

### a-8. 등록정보 (review-app-registration-info.md)

- 기본 정보(앱 이름, 설명, 개발자 이름, 아이콘·이미지)는 정확해야 하며 욕설·폭력·선정·범죄 묘사, 실적·순위·매출 등 사실 확인 어려운 내용, 텍스트 이모티콘·반복 특수문자 금지.
- 앱 제목은 **30자(영문 기준)** 초과 금지. 타 앱과 유사·중복·오해 소지 금지.
- 서비스 국가별 언어로 모든 메타데이터 제출. 부적합 카테고리는 임의 변경될 수 있음.
- 아이콘: 최대 1 MB, 512 x 512 px, PNG/JPEG. 아이콘 이미지 별도 제출.
- 앱 이미지: 주요 기능 설명 **최소 2장**, 최대 8장, 1장당 최대 8 MB, PNG/JPEG. 업로드 순서대로 슬라이드. 실제 앱과 다르면 거부.
- 패키지: APK, 압축 최대 파일 크기 600MB 미만.
- 개인정보 처리, 위치정보 활용, 접근 권한(카메라, 저장 공간, 마이크, GPS, 전화, 연락처 등), 광고성 정보는 정직하게 설정. 앱 전용 개인정보처리방침이 없으면 회사 공통 방침 제출 가능. **위치정보 서비스 사용 시 '위치정보 이용약관 및 기타 정보 처리방침' 함께 제출.**
- 로그인 필요 앱: 관리자·게스트가 아닌 **실제 사용자 기준의 전체 권한 계정** 제출(웹 앱도 동일).
- 증빙 서류: 타사 지적재산권 사용 권리, 정부 기관 대행/허가(정부 기관이 제출), 웹 앱의 도메인 등록 확인증(호스팅 업체 발급), 공인 의료 기관 제휴, 성인용 게임의 GRAC(게임물관리위원회) 심의 등급.
- 메타데이터 악용 시 즉시 제외 및 영구 제재.

### a-9. 사용자 개인정보 및 보안 (review-user-privacy-security.md)

- 개인정보 = 개인 식별 정보, 금융·결제 정보, 인증 정보 등, 다른 정보와 결합해 식별 가능한 모든 정보.
- 준수 사항: 접근권한 최소화. 법적 고지사항(수집 항목·처리 목적·보유 기간·동의 거부권) 안내 + **명시적 동의 절차 구현**. 목적 외 활용 금지. 개인정보 처리방침 공개, 열람청구권 등 보장. 기타는 GDPR 등 국가별 법령 적용.
- **위치정보 저장·이용 서비스**: 서비스 국가 법률·규제에 따른 등록/신고 내용 확인 요청 가능. 미확인·거짓 증명 시 판매 중단.
- 투명성: 수집 데이터 목록, 사용 방식, 공유 내용을 **앱 자체에서** 명시.
- 동의 요구사항(필수): 동의/거부 페이지·팝업이 명확할 것. 동의 버튼 외 행동(동의 없이 뒤로가기, 빈 공간 터치)을 동의로 간주 금지. 사용자가 허가한 항목의 데이터에만 접근. 아동 개인정보는 법정 대리인에게 고지 후 명시적 동의. 관련 법률 준수 증빙 또는 약관을 App Market에 제출.
- 보안: 바이러스·스파이웨어·트로이 목마·애드웨어·루팅, 스팸·DDoS·크립토재킹, 피싱·사기광고 등 금지. 시스템 보안 우회·무력화 기능 금지(확인 시 즉시 거부).
- 사용자 데이터 무단 사용 금지: 맞춤형 광고는 개인정보 활용·보관·제3자 제공 동의를 통해서만. 사용자가 제공 취소 시 **즉시 활용 경로 차단 + 저장 데이터 삭제**.

---

## (b) 앱 등록 가이드 — 보안, 태그, 업데이트, 버전 정책

출처: `app-registration-guide/app-security-guide.md`, `app-tagging-guide.md`, `app-update-guide.md`, `app-version-management-policy.md`

### b-1. 앱 보안 가이드 (app-security-guide.md) — 미준수 시 심사 불통과 가능

**민감 데이터 관리** (PII, 금융 정보, 민감한 차량 데이터, 인증 토큰, API 키·암호화 키)
- 기기 저장 민감정보는 저장 위치 무관 암호화: 앱 내/외부 저장소, 캐시, SQLite, SharedPreferences 동일 적용. 임시 파일·사본에도 평문 금지.
- 출시 전 디버깅 로그 비활성화 또는 민감정보 마스킹.
- 캐시에 민감정보 저장 비권장, 필요 시 암호화 후 사용 종료 시 삭제.
- 자동 백업: 현재 Pleos Connect의 **non-GAS AAOS 환경에서는 Google 기반 앱 데이터 자동 백업이 동작하지 않음**. 타 Android 환경 병행 제공 시 미사용 백업 비활성화·민감 파일 백업 제외 권장.
- 민감정보는 앱 전용 내부 저장소에. 외부 저장소 사용 시 암호화.
- URL 경로/쿼리 파라미터에 인증 토큰·세션·비밀번호 금지 → Authorization 헤더 등 사용.

**안전한 암호화**
- 서명·무결성에 MD5, SHA-1 대신 SHA-256 등. 블록 모드는 AES-GCM 권장, ECB 회피. RSA는 SHA-256 기반 OAEP 패딩 고려(AES-GCM은 패딩 불요). AES-GCM에서 동일 키로 IV/nonce 재사용 금지. 암호학적 안전 난수 생성기 사용.
- 키는 Android Keystore로 보호. 소스코드·리소스·설정 파일 하드코딩, 보호 데이터와 평문 동반 저장 금지.

**통신**
- 외부 통신은 HTTPS 등 암호화 프로토콜. **TLS 최소 1.2 이상 필수, TLS 1.3 권장**. SSL 및 TLS 1.0, 1.1 연결 차단. 검증된 Cipher Suite(TLS 1.2 사용 시 구성 검토).
- 신뢰 서버와만 통신. 서버 원격 명령 실행 기능은 출처·유효성·무결성 확인 및 범위 제한.

**컴포넌트·권한**
- 권한 최소화(Pleos Connect에서 실제 필요한 권한 기준). 외부 호출 불필요한 Activity, Service, BroadcastReceiver, ContentProvider 비노출. 민감정보는 수신 대상 명시(암시적 브로드캐스트 금지). 공개 컴포넌트는 호출자 인증 + 접근 권한 모두 확인. WebView 로컬 파일 접근 제한. 외부 URI·경로 검증·정규화.

**개발·배포**
- **Pleos가 공식 제공·허용한 API만 사용**, 미허가 API 포함 금지. 차량 관련 API는 앱 기능과 직접 관련된 범위에서만.
- 토큰·비밀 키 하드코딩 금지. 서버 IP 고정 대신 도메인 등 관리 가능 방식.
- 네이티브 라이브러리: RELRO, 스택 카나리(Stack Canary), NX 비트 보호 권장. SDK·라이브러리 취약점 주기 점검.
- **배포 앱은 테스트/디버그 인증서 대신 정식 배포용 서명 인증서 사용.** 앱 디버깅, 디버그 빌드 설정, WebView 원격 디버깅 비활성화.

**WebView / 브라우저**
- WebView: 로드 콘텐츠·이동 사이트 범위 제한, JavaScript·JS 브리지는 필요 시에만.
- 웹브라우저 앱 추가 요건: 보안 업데이트 지원 엔진, 악성 사이트 접근 제한, HTTP→HTTPS 우선 연결 및 비암호화 경고, 서버 인증서 신뢰 체인·유효기간·호스트명 검증(무조건 신뢰 금지), SOP/CORS 준수, **CSP Level 2 이상** 지원, 샌드박스 유지, 개발자 모드·프록시 변경·확장 프로그램 설치 비활성화, 파일 다운로드 제한.

### b-2. 앱 태그 표기 가이드 (app-tagging-guide.md)

- 적용: 앱 타이틀 원문이 외국어이고 한국어 타이틀을 병행/대체 표기하는 앱.
- **영문 태그 필수**: 공식 앱명과 동일하게 표기, 임의 축약·변형 불허. 한국어 태그는 선택. 다국어 공식 타이틀은 각 언어 명칭 태그 가능하되 영문 태그 필수.
- 예: 공식명 `Sport` / 노출 타이틀 `스포츠` / 필수 태그 `Sport` / 선택 태그 `스포츠, 운동 등`.
- 미준수 시 반려 또는 수정 요청.

### b-3. 앱 업데이트 가이드 (app-update-guide.md)

- **Pleos App Market은 자동 업데이트를 지원하지 않음** → 앱이 업데이트 시퀀스(버전 확인 → 안내 팝업 → 앱 마켓 이동 → 업데이트 실행)를 제공해야 함.
- 프로세스:
  1. 앱 실행 시 최신 버전 확인(개발사 구현).
  2. 최신이 아니면 안내 팝업 출력: `최신 버전이 존재합니다. 업데이트를 위해 페이지를 이동할까요? (Yes, No)` (개발사 구현).
  3. Yes → 앱 상세 페이지 이동, 경로 `market://detail?package={페키지명}` (개발사 구현).
  4. 앱 마켓이 로그인 확인: 미로그인 시 로그인 창 → 완료 후 마지막 페이지 또는 마켓 홈으로 이동하고 프로세스 종료. 로그인 상태면 상세 페이지 → 사용자가 **[업데이트]** 버튼으로 진행.
  5. No → 개발사 정책에 따라 앱 사용 유지 또는 종료(개발사 구현).
  6. 최신 버전이면 실행 유지.
- 역할 분담: 버전 체크 ✅개발사 / ⛔마켓(SDK 제공 차후 고려), 안내 메시지 출력 ✅개발사, Yes 이동 경로 ✅개발사, 로그인 확인·처리 ✅마켓, No 선택 시 조치 ✅개발사.

### b-4. 앱 버전 관리 정책 (app-version-management-policy.md)

- 배포 또는 심사 요청 시 **반드시 앱 버전 변경**. 기능·버그·설정 변경 등 모든 변경은 버전 업데이트로 관리.
- 버전 미변경 시 변경 이력 확인 불가 → 심사 미진행 또는 반려.

---

## (c) App Integration Guide — Gleo Interface (음성 비서 연동)

출처: `app-integration-guide/guide/core-concepts.md`, `app-capability.md`, `action_callback.md`, `deliver-schema.md`, `examples.md`, `use-cases/radio-app.md`

### c-1. 개념 (core-concepts.md)

- **Gleo Interface**: Gleo AI(차량 음성 AI 에이전트)와 타사 앱이 음성 명령을 처리하기 위해 상호작용하는 방식. 앱이 정의한 function call을 LLM이 생성하고 앱 응답으로 최종 답변을 만드는 **function calling** 방식(OpenAI 방식과 유사, Android 지원용 스키마 확장).
- 앱은 Gleo AI가 호출할 function과 호출 방식을 정의한 JSON **App Schema**를 등록. Gleo AI는 **Gleo Action**으로 앱을 호출하고 **Callback**으로 응답 수신. 가이드는 지속 개선 예정.

### c-2. App Schema (app-capability.md) — JSON Schema 기반

- 필드: `packageName`(string), `appDescription`(string), `version`(string, 마켓 등록 버전), `actionCalls`(array, **최대 3개**).
  - 각 항목: `type`("function" 고정), `function`{ `component`(string, 기본 "activity"), `name`, `description`, `parameters` }.
  - `component`: `"activity"` → `context.startActivity()`로 실행(포어그라운드 UI·사용자 상호작용 필요 시). `"service"` → `context.sendBroadcast()`로 전송(UI 없는 백그라운드 처리).
  - `name`: 앱 내 고유, **모두 대문자 + 단어 구분 밑줄(_)**.
  - `parameters`: `type`("object" 고정), `properties`(각 property는 `type`, `description`, 선택적 `enum`), `required`(array<string>).
- 예시 스키마(packageName `ai.dot42.pleos.reminderapp`)에는 `"invokeWord" : "리마인더"` 필드도 포함되어 있음(필드 설명 목록에는 미기재). function: `ADD_REMINDER`(title, dateTime ISO 8601), `SEARCH_REMINDER`(searchTerm), `DELETE_REMINDER`(searchTerm).
- 작성 가이드라인:
  - `actionCalls`는 최대 3개(나머지 기능은 화면 상호작용으로).
  - function 이름은 고유, 단독으로 명령을 완수하도록 설계, 동사만이 아닌 2~3개 단어로 대상 포함.
  - **모든 description은 영어**. Gleo AI 동작 조작·타 앱 억제 문구 금지. 구체적 예시 금지. 한 문장(최대 두 문장).
  - parameter `type`은 string, number, boolean, array 중 **하나만**(`["string","null"]` 같은 배열 형식 미지원). `enum`은 문자열 값만, `null` 미지원.
  - `required`에는 필수 parameter만(선택 parameter 미포함). Gleo AI가 모든 필수 값을 보장하진 않지만 성공률 향상.

### c-3. Callback Schema (action_callback.md)

- 필드: `functionName`(string, 필수, App Schema의 function name과 일치), `status`(enum 필수: `"success"`, `"asking"`, `"fail"`), `message`(string 필수, 영어 완전한 문장, 검색어·장소명 등 타 언어 콘텐츠 포함 가능), `mode`(enum 선택, 기본 `"voice"`; `"beep"` 효과음만; `"silent"` 무음 — 앱이 자체 TTS/음악 재생 시), `data`(string 선택, JSON 객체 배열 문자열, 필요한 정보만).
- status 상세: `success` = 정상 처리(결과 없음도 가능). `asking` = 추가 정보(필수 parameter)나 사용자 확인 필요. `fail` = 비즈니스 로직 실패(precondition, conflict, domain constraint) + 시스템 오류(crash, network failure, server timeout).
- **Gleo는 timeout으로 7초 후 Action을 취소.**
- `mode: "silent"` 시 Gleo AI는 사용자 상호작용에 접근할 수 없어 Callback에 전적으로 의존. `silent`/`beep`에서도 `data` 포함 권장(문맥 연속성). `data` 반환 강력 권장.

### c-4. 스키마 전달 (deliver-schema.md)

- 파일명 **`schema.json`** 고정, 위치 **`assets/schema.json`**.
- 전달받은 스키마를 Pleos가 가이드라인 준수 검토 → 승인 후 통합 자동 활성화.
- `AndroidManifest.xml`에 function마다 action filter 정의:
  ```xml
  <activity android:name=".YourActivity">
    <intent-filter>
      <action android:name="ADD_REMINDER" />
      <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
  </activity>
  ```
- Gleo AI 앱 부팅 시 `AssetManager`로 모든 앱의 `assets/schema.json`을 스캔해 DB 저장(**부팅 시 1회**). AAOS에서 앱 설치(ADB 또는 마켓)/삭제 시 DB 갱신.

### c-5. 코드 예시 (examples.md)

- Gleo Action JSON: `{ "packageName", "action", "actionParameters": {...} }` → Intent 변환: `action`→Intent action, `packageName`→`setPackage`, `actionParameters`→`putExtra`.
- 실제 코드에서는 extra 키 **`"ACTION_PARAM"`**에 JSON 문자열로 전달. 수신 측은 `intent.extras.getString("ACTION_PARAM")` → `JSONObject` 파싱(`component="activity"`는 Activity의 `onCreate`, `"service"`는 `BroadcastReceiver.onReceive`에서 `intent.action` 비교).
- Callback은 Broadcast로 전송(Gleo AI는 Android 서비스): `Intent("ai.pleos.playground.intent.action.APP_ACTION_RESPONSE")`에 `putExtra("packageName", packageName)`, `putExtra("response", jsonObject.toString())` 후 `sendBroadcast`.

### c-6. 라디오 앱 유스케이스 (radio-app.md)

- packageName `ai.umos.mediaapp`. function: `SWITCH_BAND`(band enum `FM`/`AM`, component service), `SEARCH_STATION`(searchTerm, activity), `PLAY_FREQUENCY`(frequency number, FM은 MHz/kHz, AM은 kHz, service).
- Callback 예: success(data에 frequency/frequencyBand/stationName/channelName), fail(채널명·주파수 확인 요청), asking(다수 일치 시 후보 목록을 data에 담아 선택 요청).

---

## (d) 오디오 포커스 & Suspend 모드

출처: `vehicle-app-developer-guide/audio-focus-development-guide/audio-focus-development.md`, `suspend-mode-audio-focus.md`

### d-1. 오디오 포커스 개발 가이드

- AAOS 오디오 포커스를 기반으로 하되 **Pleos Connect 오디오 포커스 정책(Pleos Connect Interaction Matrix v1.0, 2026-01-28)** 필수 준수.
- 상호작용: R(Reject, 거부), E(Exclusive, 선점), C(Concurrent, 동시 — 보통 duck 기반). C 성립 조건: 들어오는 요청이 `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` 사용. 다중 보유자 시 **R > E > C** 우선순위로 가장 보수적 결과.
- 컨텍스트(CarAudioContext): MUSIC, NAVIGATION, VOICE_COMMAND, CALL_RING, CALL, ALARM, NOTIFICATION, SYSTEM_SOUND, EMERGENCY, SAFETY, VEHICLE_STATUS, ANNOUNCEMENT.
- Usage 매핑: MUSIC ← MUSIC/UNKNOWN/GAME/MEDIA; NAVIGATION ← NAVIGATION/ASSISTANCE_NAVIGATION_GUIDANCE; VOICE_COMMAND ← VOICE_COMMAND/ASSISTANT; CALL_RING ← CALL_RING/NOTIFICATION_RINGTONE; CALL ← CALL/VOICE_COMMUNICATION/VOICE_COMMUNICATION_SIGNALING; ALARM; NOTIFICATION ← NOTIFICATION/NOTIFICATION_*; SYSTEM_SOUND ← SYSTEM_SOUND/ASSISTANCE_SONIFICATION/ASSISTANCE_ACCESSIBILITY; EMERGENCY; SAFETY; VEHICLE_STATUS; ANNOUNCEMENT. 시스템 컨텍스트는 Android 11 이상.
- **구현 규칙**:
  1. 각 오디오 스트림은 재생 전 `AudioAttributes` + `AudioFocusRequest`로 포커스 요청.
  2. C 기대 컨텍스트(예: NAVIGATION↔MUSIC)는 `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`.
  3. E 필요 컨텍스트(CALL, EMERGENCY)는 목적에 맞는 gain, 포커스로 모든 것을 강제 제어하지 않음.
  - 포커스 변경 이벤트 처리 **필수**(AAOS 필수는 아니나 Pleos 정책상 필수): `AUDIOFOCUS_GAIN`→정상 재생, `AUDIOFOCUS_LOSS`→즉시 중단, `AUDIOFOCUS_LOSS_TRANSIENT`→일시정지 후 복귀, `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK`→볼륨 감쇄.
  - Duck: `LOSS_TRANSIENT_CAN_DUCK` 수신 시 볼륨 감쇄(-X dB), 해제 조건에서 원복. duck 중 pause하면 C가 깨질 수 있음.
  - 다중 홀더: `result = C`로 시작, 모든 currentHolder에 대해 `cell = MATRIX[currentHolder][incoming]`, `result = min_by_severity(result, cell)`(R 최강) → grant/steal/fail.
- 컨텍스트별: EMERGENCY current 시 대부분 incoming R, incoming EMERGENCY는 대부분 E 선점, EMERGENCY-EMERGENCY는 C. SAFETY는 EMERGENCY 아래(EMERGENCY current면 SAFETY R). CALL 중 MUSIC 요청 R, CALL 중 NAVIGATION C(옵션 플래그로 C→R 변경 가능). NAVIGATION↔MUSIC C 기본(NAVIGATION은 MAY_DUCK 요청). **VOICE_COMMAND(Gleo)**: 일반 오디오보다 우선이되 EMERGENCY·CALL 등 Reject 컨텍스트엔 우선하지 않음. Gleo 호출로 `AUDIOFOCUS_LOSS_TRANSIENT` 수신 시 즉시 재생 중단, 필요 시 abandon.
- 매트릭스 하드코딩 금지 권장 → `interaction_matrix.json` 또는 csv 로딩, 키 `current_context`, `incoming_context` → 값 E|C|R.
- 로그 필수: `incoming context`, `gain type`, `client id`, current holders 목록, holder별 매트릭스 결과, 최종 결과 및 처리(grant/steal/reject), duck 적용/해제(대상 스트림, 감쇄 값).
- 검증 체크리스트(필수): ① MUSIC 중 NAVIGATION → C ② CALL 중 MUSIC → R ③ MUSIC 중 CALL → E ④ EMERGENCY 중 어떤 요청 → R ⑤ MUSIC+NAVIGATION 동시 홀더 상태에서 VOICE_COMMAND → 보수적 결과 확인.

### d-2. Suspend 모드 오디오 포커스

- Suspend 모드 = 시동(IGN) OFF 후 완전 종료 없이 저전력 상태. 디스플레이·UI 비활성, 오디오·미디어 중단, 일부 시스템 작업(Garage mode 등) 제한적 수행, 빠른 Resume용 상태 유지. 앱은 '언제든 중단되고 다시 시작될 수 있는 상태'로 간주.
- 기본 원칙(위험 표기): 진입 시 모든 일반 오디오 세션 출력 금지, **보유 포커스 반드시 반납(abandon)**. Resume 후 자동 복원 없음. 상태 저장 후 필요 시 재요청.
- 진입 순서: ① 재생 즉시 중단 ② 포커스 abandon ③ 플레이백 상태 저장(재생 여부, 위치) ④ player/track 등 리소스 해제. 이 과정에서 새 재생·포커스 요청 불허.
- 예외(최소 범위): 전화 통화·긴급 통신, 안전 경고음, 시스템 필수 음성 안내.
- Resume: 포커스 자동 복구 없음, 앱이 상태 재평가 후 재요청, 자동 재생 여부는 OEM/사용자 정책.
- 준수: Suspend 중 포커스 유지 금지, 백그라운드 재생 금지, Resume 대비 포커스 선점 금지.
- Kotlin 예제: `AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)`, `onSuspendEntering()`(pausePlayback → savePlaybackState → abandonAudioFocus → releaseTransientResources), `onResumeFromSuspend()`(restorePlaybackState 후 `shouldAutoResume = false` 기본), `audioManager.abandonAudioFocusRequest(audioFocusRequest)`.

---

## (e) Music/Audio 및 Maps/Navigation 앱 가이드 — 타 카테고리에 일반화되는 부분

출처: `vehicle-app-developer-guide/music-audio-app-developement-guide.md`, `navigation-app-development-guide.md`

### e-1. Music/Audio 가이드에서 일반화되는 규칙

- **카테고리 불일치는 심사 반려 사유**: 부가 기능(커뮤니티, 쇼핑 등) 포함 시 핵심 기능 기준으로 판단. 보조 기능으로만 음악을 제공하는 앱은 즉시 반려. 하위 카테고리(음악/팟캐스트/오디오북/라디오) 미정의 → 기능 보완 요청, 반복 위반 시 노출 제한·등록 중단.
- 매니페스트 카테고리 선언: `<application android:appCategory="audio" />`.
- 오디오를 출력하는 모든 앱: `MediaBrowserService`(MBS) + `MediaSession` 구현 필수(미구현 시 배포 제외). 미디어 센터(MBS 기반 템플릿) 권장 — 스티어링 휠 키·미디어 앱 라이브러리·Tune Knob·Media 버튼 연동 기본 제공. 미사용 시 스티어링 휠 키는 별도 구현 필요(미구현 시 심사 반려 가능), 라이브러리·Tune Knob·Media 버튼 미지원. 차량 Mode 버튼은 양쪽 모두 미지원.
- 스티어링 휠 키: `MediaSessionCompat` 등록 + `MediaSessionCompat.Callback` 구현, 최소 재생/일시정지/정지/미디어 ID 기반 재생/검색 기반 재생, 가능하면 이전/다음 트랙.
- **자동 재생 금지**: 앱 연결 시, 프로필 변경 시 자동 재생 금지. 사용자 명시적 동작 또는 시스템 재개 요청에만 재생.
- **서비스 생명주기 규칙(모든 백그라운드 오디오 앱에 적용 가능)**:
  - `AUDIOFOCUS_LOSS` 수신(게임 앱 실행 등) 시 `stopPlayback()` + `abandonAudioFocusRequest`, `MediaSession`을 active로 유지하지 않음. `LOSS_TRANSIENT`/`LOSS_TRANSIENT_CAN_DUCK` → pause, `GAIN` → resume.
  - `bind()`/`unbind()`만 쓰면 `onTaskRemoved()` 미수신 가능 → `onCreate()`에서 `startService()`로 Started 상태 구성. `onStartCommand()` 및 재생 시점에 `startForeground()` 호출.
  - `onTaskRemoved()`에서 `stopMedia()` → `stopSelf()` → `super.onTaskRemoved()` 순서.
  - 쓰리핑거 동작 시 `MediaSessionCompat.Callback.onStop()` 호출 가능 → `ExoPlayer.stop()` 수행.
- Gleo AI 연동 2가지: Gleo Interface 구현, 또는 Android 표준 미디어 API(MediaAgent가 Media3 기반으로 제어; `MediaSession`/`MediaLibraryService` 연동, 기본 제어 명령, 검색 명령). 곡 검색 음성 기능은 Media3 `MediaLibraryService` 검색 구현 필수. 곡 검색 외 핵심 기능 약 3~5개 추가 음성 기능 선택 제공 가능.

### e-2. Maps/Navigation 가이드에서 일반화되는 규칙

- 기본 정책: 운전자 안전 최우선, 주행 중 조작 최소화, 카테고리 기반 기능 제한, Pleos Connect 플랫폼 API + AAOS 표준 API 사용 및 UX 가이드 준수.
- **모든 앱은 개발·등록 시 카테고리 선언 필수.** 매니페스트 선언(내비의 경우 `android.intent.action.MAIN` + `android.intent.category.APP_MAPS` 인텐트 필터)과 마켓 등록 카테고리 일치. 불일치 시 즉시 반려. **심사 중 기능 변경 시 반드시 재심사 요청.**
- 단일 경로 정책: Active Route(음성·TBT 허용) / Inactive Route(안내 금지) / Background(모든 안내 금지). 내비 간 및 내비↔Phone Projection 간 적용. `CarAppFocusManager.requestAppFocus`, `CarAppFocusManager.OnAppFocusChangedListener`로 경로 소유권 관리. 경로 전환은 사용자 명시적 선택 또는 기존 앱 오류·종료 시에만, 전환 시 사용자 인지 안내 + 동의 없는 자동 전환 금지.
- **주행 중 조작 금지 항목**: 복잡한 메뉴 탐색(3 depth 초과), 긴 리스트 스크롤, 영상·애니메이션·광고 노출. **허용**: 음성 입력, 단일 터치 기반 주요 기능, 즐겨찾기 목적지 선택.
- UX 심사: 터치 영역 최소 크기 충족, 주행 핵심 정보 상시 노출, **주행 중 전체 화면 팝업 금지**.
- 시스템 연동: 공식 API 권장, **비공식 센서/API 사용 금지**.
- 권한: 위치 권한 필수(내비), 마이크는 음성 입력 시에만, 불필요 권한 요청 시 반려.
- 성능: 실행·전환 지연 최소화, 장시간 주행 안정성, Crash·freeze 발생 시 반려.
- 보안·개인정보: **위치 데이터 암호화 처리, HTTPS 통신, 개인정보 처리방침 필수 제공, 불필요한 위치 데이터 저장 금지.**
- Immediate Reject: 주행 중 광고 노출, 영상·게임 요소 포함, 복수 내비 동시 경로 안내, 카테고리 위반 기능, OEM/AAOS 정책 위반.

---

## (f) CCP(Center Control Panel, 로터리 컨트롤러) 지원 정책

출처: `vehicle-app-developer-guide/ccp-rotary-controller-development.md`

- CCP = Rotary Controller, 다이얼, 물리 버튼 등 하드키 입력 장치. CCP Back 하드키는 이전 화면 이동 또는 앱/시스템 레벨 Back.
- 앱 분류별 요건:
  - **User-installed 앱**(마켓 배포): CCP 지원 선택. 미지원이어도 등록·배포 제한 없음. 단 **필수 조작을 터치만으로 완결** 가능해야 함.
  - **Pre-installed 앱**(기본 탑재): CCP 지원 필수. 계약서에 "CCP 지원" 의무 조건 명시, 초기 릴리즈부터 회전·클릭·방향 이동 지원, 업데이트에서 누락·비활성화 불허, 미지원·불완전 시 승인 불가. 등록 전 CCP 심사(포커스 이동, 회전 스크롤, 클릭 선택, 역방향 입력, 터치 전용 영역 부재, 운전 중 차단 요소 준수) 합격 필요.
  - 예외(계약서 명시): ① 정적 콘텐츠 중심·단일 화면 정보 표시형 ② 브라우저/특정 미디어 앱처럼 WebView 포커스 제약 등 기술적 제한 ③ 게임처럼 UX·안전상 부적합.
  - **System 앱**: 플랫폼/OEM 시스템 이미지 포함.
- 브라우저 앱: CCP 회전→스크롤(상/하), 클릭→링크/버튼 선택, 좌/우→포커스 이동(주소창, 북마크/탭/홈, 뒤로/앞으로, 설정, 기본 메뉴). 웹페이지 내부 콘텐츠 포커스는 전면 보장 불요.
- **CCP 미지원 선언** (`<application>` 하위):
  ```xml
  <meta-data android:name="com.hmg.rotary.ROTARY_SUPPORTED" android:value="false" />
  ```
- **CCP Back 하드키 앱 숨김 정책(필수, 심사 반려 기준)**:
  - 앱 최상단 포커스에서 Back 입력 시 앱을 종료하지 않고 **숨김(Hide)**. 숨김 후에도 미디어 사운드 등 백그라운드 기능은 서비스 생명주기·오디오 포커스 정책에 따라 유지. 하위 화면·팝업에서는 기존 Back 정책(이전 화면/팝업 종료).
  - CCP Back이 Android 표준 Back 이벤트로 전달되는지 확인 → `OnBackInvokedDispatcher` 또는 기존 Back API 사용. 팝업/하위/최상위 화면 구분.
  - 숨김은 `moveTaskToBack(true)` 호출로 구현. `finish()`/`finishAffinity()` 금지. 백그라운드 이동 후 프로세스가 종료될 수 있으므로 상태 저장·복원.
  - 최상단에서 앱 종료되거나 `moveTaskToBack(true)` 기반 숨김 미구현 시 심사 반려 가능.
- UX 가이드라인 파일: `Pleos-Connect-CCP-UX-guideline-20260220.fig`. 정책은 AAOS 버전·차량 플랫폼·차종별로 업데이트될 수 있고, HMI/안전 기준 미준수 시 배포 제한·기본 탑재 제외. 가이드라인 갱신 시 Pre-installed 개발사에 업데이트 요청 가능.

---

## (g) IME 표시에 따른 window resize 처리

출처: `vehicle-app-developer-guide/ime-window-resize-handling-guide.md`

- IME 표시 시 IME insets만큼 view 크기를 조정하는 것은 Pleos 정책이 아닌 **AOSP 기본 동작**.
- Pleos Connect 사양: `EditText`/`TextField`가 포함된 window → IME에 의해 view가 위로 밀려 올라가는 것이 사양. **포함되지 않은 window → IME 표시 시 view가 앵커링되지 않아야 함(앱에서 수정 필요)**.
- 제외 방법: Dialog의 insets 적용 대상을 `statusBars`와 `navigationBars`로 한정.
  - System Dialog: `WindowManager.LayoutParams`에 `params.setFitInsetsTypes(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())` 후 `windowManager.addView(overlay, params)`.
  - App Dialog: `dialog.window.attributes`에 동일하게 `setFitInsetsTypes` 적용 후 `window.attributes = params`, `setContentView`, `show()`.
- IME 외 insets(SystemBars)에 대한 view 업데이트는 `View.OnApplyWindowInsetsListener`에서 개별 처리. Activity 유형은 Google의 "Handle input method visibility" 가이드 기준으로 별도 검토.

---

## (h) TTS / 음성 안내 선행 묵음(pre-roll silence)

출처: `vehicle-app-developer-guide/voice-guidance-preroll-silence-guide.md`

- 목적: 초기 음절 손실, 재생 시작부 클리핑, 출력 경로 초기화 지연 방지. 적용 범위: TTS 음성 안내, 녹음 음원 안내 멘트, 내비게이션·알림·상태 안내 등 모든 음성 출력 경로.
- **선행 묵음 150 ms ~ 200 ms 권장**. 최소 권장값 150 ms. 150 ms 미만은 첫 음절 손실 가능, 200 ms 초과는 체감 지연 → 예외적으로만.
- 원칙: 묵음 없이 즉시 재생하지 않음. 일관된 기준 적용. 서비스/플랫폼/디바이스 편차가 있어도 기본 정책 동일 유지.
- 적용 위치 우선순위: ① PCM/WAV 등 오디오 데이터 앞단에 silence padding 삽입 ② TTS 결과물 후처리로 묵음 추가 ③ 플레이어 재생 지연(스케줄링 편차가 커 비권장).
- 연속 재생: 단일 안내 시작 시 적용, 하나의 발화 단위로 이어지는 연속 음성에는 추가 묵음 삽입 금지, 스트림 끊김·새 세션 시작 시 재적용.
- 함께 확인: 오디오 출력 경로 사전 초기화, 오디오 포커스/세션 확보 완료, 디코더/플레이어/출력 디바이스 재생 가능 상태. 선행 묵음은 클리핑 완화 수단일 뿐 출력 경로 초기화 문제를 대체하지 않음.

---

## (i) 지역별 주행 규제 정책 (InfoLINK 추가 구현 항목)

출처: `infolink-emulator/driving-restiction-policy.md`

- InfoLINK 앱도 Pleos Connect 지역별 주행 규제 정책을 따라야 하며, 추가 구현 항목을 보충.
- 주행 상황 구분: Parking, Idling, Driving. 지역 구분은 **`product.sw.target_region`** 시스템 프로퍼티: 한국 `KOR`, 유럽 `EUR`, 북미 `USA`, `CAN`.
- **QR 코드 화면 규제**(`CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP_QR_SCAN`, 사유: 휴대전화 활용 유도): 주행 규제 적용 상태에서 한국(`KOR`)은 대부분 ✅, 북미·유럽·기타 지역은 ⛔. 잠금 처리 방식은 화면별로 "QR 코드 이미지를 다른 이미지로 대체"(BYOD QR, 고객센터 인증 코드 QR, 프로필 QR) 또는 "콘텐츠 영역 차단"(Wi-Fi URL QR, What's New QR, 매뉴얼 QR, 서비스 점검 QR). Wi-Fi 연결 > URL QR 코드만 한국에서 규격 미정(No Spec).
- **주행 규제 판단 기준**: 차량 하드웨어 신호(속도, 기어, 브레이크 등) 기반, 지역별 해제 조건 상이.
  - **북미**: Auto Hold를 정차로 인정하지 않음. 해제 조건 = 시동(IGN1) 또는 엔진(구동 준비) OFF, 또는 주행 속도 **5 km/h 이하**이면서 자동 변속기는 기어 P(Parking), 수동 변속기는 Parking Brake ON + 기어 N(Neutral).
  - **북미 외**: Auto Hold를 정차로 인정. 해제 조건 = IGN1/엔진 OFF, 또는 5 km/h 이하이면서 기어 P / Parking Brake ON / Auto Hold Active(장착 차종) 중 하나. 조건 미충족이거나 5 km/h 초과 시 규제 ON → 서비스 이용 제한.

---

## (j) 결제 시스템 및 외부 라이브러리 마이그레이션 (in-prep, 준비 중 문서)

출처: `in-prep/_payment-system-migration.md`

- **인앱 결제**: 모바일용 Google Play Billing 등 외부 의존성 제거. 차량 내 인앱 결제는 플랫폼 전용 **Payment SDK** 사용이 원칙. 정기결제·일회성 구매·쿠폰 로직을 플랫폼 정책에 맞춰 재설계. **Payment SDK는 현재 준비 중, 상세 사양은 오픈 시점에 별도 안내.**
- **광고**: 운전자 주의 분산 규제로 엄격 제한. 정차 상태, 보조 화면, 승객 전용 디스플레이 등으로 노출 범위 제한 가능. 모바일식 광고 시나리오는 전면 재디자인.
- **서드파티 SDK**: 미사용 SDK 제거(슬림화). 위치·마이크·카메라 접근 SDK는 차량용 보안 가이드라인 부합 여부 확인 필수.

---

## EV 앱 개발 시 특히 유의할 점

EV 대시보드 / 충전 / 주행거리 앱 관점에서 위 문서들로부터 직접 영향을 주는 항목이다.

- **Vehicle SDK 퍼미션은 사전 승인제**: 배터리 SOC·충전 상태·주행거리 등 차량 데이터 API를 쓰려면 해당 퍼미션을 매니페스트에 선언해야 하고, 그 퍼미션은 Pleos App Market과 사전 협의·승인된 경우에만 선언 가능. 미승인 퍼미션 선언 시 심사 불통과. 제출 시 "SDK 추가 권한" 항목에 My Project > 프로젝트 정보 > 프로젝트 ID 입력 필수.
- **승인된 API만 사용**: Pleos가 공식 제공·허용한 API 외 비공식 센서/API 사용 금지(보안 가이드 + 내비 가이드 공통). 차량 API는 앱 기능과 직접 관련된 최소 범위만 호출.
- **민감한 차량 데이터는 민감정보로 취급**: 기기 저장 시 암호화(SharedPreferences, SQLite, 캐시 포함), 로그 마스킹, URL 파라미터 노출 금지, Android Keystore 키 관리, TLS 1.2 이상(1.3 권장), HTTPS 필수.
- **위치 데이터 규칙(충전소 검색·경로 계산 시)**: 제출 시 "위치정보 활용" 선택 → '위치정보 서비스 이용약관 및 기타' URL이 조건부 필수. 위치 데이터 암호화 처리, 불필요한 위치 데이터 저장 금지, 국가별 위치정보 사업 등록/신고 확인 요청 가능(거짓 시 판매 중단). 위치 권한은 필요한 최소 범위, 마이크는 음성 입력 시에만.
- **개인정보 동의 UX**: 동의/거부 팝업 명확, 동의 버튼 외 행동(뒤로가기, 빈 공간 터치)을 동의로 간주 금지, 허가한 항목만 접근, 취소 시 즉시 경로 차단 + 저장 데이터 삭제. 수집 항목·목적·보유 기간·거부권을 앱 내에서 명시.
- **주행 규제 동작**: 지역(`product.sw.target_region` = KOR/EUR/USA/CAN)과 주행 상태(Parking/Idling/Driving)에 따라 기능 제한. 규제 해제 기준은 속도 5 km/h 이하 + 기어 P 등이며 북미는 Auto Hold 불인정. 충전소 결제용 QR·앱 연동 QR 등 QR 노출 화면은 `UX_RESTRICTIONS_NO_SETUP_QR_SCAN` 대상으로 북미·유럽·기타 지역에서는 주행 중 ⛔(이미지 대체 또는 콘텐츠 영역 차단).
- **주행 중 UI 제약**: 3 depth 초과 메뉴, 긴 리스트 스크롤, 영상·애니메이션·광고 노출 금지. 전체 화면 팝업 금지. 운전과 무관한 헤드업 알림 금지. 핵심 주행 정보 상시 노출, 단일 터치·음성 입력 중심 설계. 광고는 정차 상태·보조 화면·승객 디스플레이로 제한.
- **성능 기준(심사 반려 기준)**: 터치 응답 0.25초, 앱 실행 10초, 콘텐츠 로드 10초, 버튼 반응 2초(초과 시 진행 중 UI 표시). 과도한 CPU·배터리 점유는 차량 성능·안전 직결 항목으로 심사. 크래시·freeze 시 반려. 암호화폐 채굴·크립토재킹 금지.
- **디스플레이 호환성**: 레터박스 없이 화면 채우기, 호환성 모드 금지, 해상도 전환 시 상태 유지·기능 패리티, 컷아웃·시스템 바·소프트웨어 키보드 안전 영역 준수. EditText 없는 window(대시보드형 화면)는 IME 표시 시 밀리지 않도록 `setFitInsetsTypes(statusBars or navigationBars)` 적용.
- **CCP 요건**: 마켓 배포(User-installed) 앱은 CCP 선택 사항이나 필수 조작을 터치만으로 완결 가능해야 하고, 미지원 시 `com.hmg.rotary.ROTARY_SUPPORTED=false` 메타데이터 선언. Pre-installed로 탑재될 경우 CCP 지원 필수(정적 정보 패널형은 예외 가능). 최상단에서 CCP Back 입력 시 `moveTaskToBack(true)`로 숨김(`finish()` 금지), 미구현 시 반려.
- **Suspend 모드(IGN OFF)**: 저전력 진입 시 오디오 즉시 중단 + 포커스 abandon + 상태 저장 + 리소스 해제. Resume 후 자동 복원 없음. 충전 중 알림음·안내 음성을 내는 앱이라면 이 정책과 오디오 포커스 매트릭스(EMERGENCY/SAFETY/CALL 우선, NOTIFICATION·MUSIC은 Reject될 수 있음)를 준수하고 `AUDIOFOCUS_LOSS*` 이벤트를 반드시 처리.
- **음성 안내/TTS**: 충전 완료·주행거리 안내 음성 출력 시 150 ms ~ 200 ms 선행 묵음, 출력 경로·포커스 사전 확보. Gleo 음성 연동 시 `assets/schema.json`(actionCalls 최대 3개, 영어 description, 대문자_밑줄 function명), Callback 7초 timeout, 응답 Broadcast action `ai.pleos.playground.intent.action.APP_ACTION_RESPONSE`.
- **결제**: 충전 결제·구독 기능은 Google Play Billing 대신 플랫폼 전용 Payment SDK(준비 중) 전제로 설계. 브라우저/WebView 결제 정보 저장 금지.
- **서명·빌드**: 정식 배포용 서명 인증서 사용(테스트/디버그 인증서 금지), 디버깅·디버그 빌드·WebView 원격 디버깅 비활성화, 토큰·키 하드코딩 금지, 네이티브 라이브러리 RELRO/Stack Canary/NX 적용, 불필요 SDK 제거, 미사용 컴포넌트 비노출.
- **등록 메타데이터**: 배포·심사 요청마다 버전 변경 필수. 앱 제목 30자(영문) 이내, 아이콘 512 X 512 px·1 MB, 스크린샷 최소 2장·최대 8장(8MB), APK 600 MB, 태그 최대 20개(외국어 공식명이면 영문 태그 필수), 카테고리는 매니페스트 선언과 마켓 등록이 일치해야 하며 심사 중 기능 변경 시 재심사. 심사용 실제 사용자 계정 제공, 외부 서버 사전 활성화.
- **업데이트 플로우**: 자동 업데이트 없음 → 앱 실행 시 버전 확인, 안내 팝업(`최신 버전이 존재합니다. 업데이트를 위해 페이지를 이동할까요? (Yes, No)`), `market://detail?package={페키지명}` 이동을 앱이 직접 구현.
- **자동 백업**: non-GAS AAOS 환경이라 Google 자동 백업이 동작하지 않음 → 사용자 설정·충전 이력 등은 자체 저장/동기화 설계 필요하며, 타 Android 빌드 병행 시 민감 데이터 백업 제외.
