---
created: 2026-04-24 09:00
topic: 국가법령정보센터 Open API 실사
project: laborcase
tags: [research, law, api, open-data, labor-law]
status: done
next_mode: analyze
source_research: []
---

# 🔍 국가법령정보센터 Open API 실사 리서치

> [!info] 리서치 목적
> laborcase 의 법령 조문 데이터 소스로 국가법령정보센터 Open API 를 쓸 수 있는지, 어떤 제약(쿼터·라이선스·조문 단위 접근)이 있는지 사실 확인만 한다. 결론/아키텍처 결정은 다음 분석 단계로 넘긴다.

## 배경 / 맥락
- laborcase MVP 는 부당해고·임금체불 판례 검색과 관련 조항 자동 매칭이 핵심 기능. 근로기준법 중심으로 노동 관련 6개 법령의 **조문 단위** 원문을 안정적으로 확보해야 한다.
- CLAUDE.md 법적 제약상 "잘못된 법 조항 언급 0건" 목표. 출처가 불명확한 크롤링은 피하고, 공식 API 에서 원문 + 시행일 + 개정 이력까지 받아야 근거 링크를 붙일 수 있다.
- 영문 번역본은 불필요(국내 근로자 대상), 한국어 원문만.

## 수집한 정보

### 개념 정리

- **두 개의 별도 경로가 존재**. 같은 데이터지만 창구가 다르다.
  1. **법제처 직접 (`open.law.go.kr` / `www.law.go.kr/DRF`)** — 인증 파라미터 `OC`(신청자 이메일 아이디) 사용.
  2. **공공데이터포털 (`apis.data.go.kr/1170000/law`)** — 인증 파라미터 `serviceKey` 사용, 개발/운영 계정 구분.
- **OC 의 정체**: `OC=test` 는 문서상 샘플용, 실제 호출에서는 "IP/도메인 미등록" 오류가 떨어진다. 실 사용 시 신청 시 등록한 본인 이메일 아이디(@ 앞부분)를 사용한다. 서버에서 호출할 출구 IP 와 도메인을 사전 등록해야 한다.
- **`lsId` vs `lsiSeq`(= MST)**: `lsId` 는 법령의 영속 ID, `lsiSeq` 는 **개정 버전마다 달라지는** 마스터 일련번호. 현재 시행 버전을 보고 싶으면 `lsId` 로 조회하고, 특정 시점 스냅샷은 `lsiSeq` 로 고정.
- **조문 식별자**: `JO`(조, 6자리, 예: `000300` = 3조), `HANG`(항), `HO`(호), `MOK`(목, "가/나/다"). zero-padding 필수.

### 주요 접근법 / 옵션

1. **법제처 직접 API (`DRF`)**
   - 검색 엔드포인트: `http://www.law.go.kr/DRF/lawSearch.do`
   - 본문 엔드포인트: `http://www.law.go.kr/DRF/lawService.do`
   - 조문 단위 엔드포인트: `http://www.law.go.kr/DRF/lawService.do?target=lawjosub`
   - 공통 파라미터: `OC`, `target`, `type`(HTML/XML/JSON)
   - 법령 본문 조회 파라미터: `ID`(lsId) 또는 `MST`(lsiSeq), `efYd`(시행일자 YYYYMMDD)
   - 조문 조회 파라미터: 본문 + `JO`/`HANG`/`HO`/`MOK`
   - 출처: [OPEN API 활용가이드 - 국가법령정보 공동활용](https://open.law.go.kr/LSO/openApi/guideList.do), [현행법령 본문 조항호목 조회 API](https://open.law.go.kr/LSO/openApi/guideResult.do?htmlName=lsNwJoListGuide)

2. **공공데이터포털 프록시 API**
   - URL: `http://apis.data.go.kr/1170000/law/lawSearchList.do`
   - 파라미터: `serviceKey`(필수), `target=law`(고정), `query`(검색어, 기본값 `*`), `numOfRows`, `pageNo`
   - 응답: XML only (문서상)
   - 트래픽: **개발계정 10,000건/일**, 운영계정은 활용사례 등록 후 증량 신청 가능. 이용 요금 **무료**.
   - 승인: 개발/운영 모두 자동승인.
   - 출처: [법제처 국가법령정보 공유서비스 | 공공데이터포털](https://www.data.go.kr/data/15000115/openapi.do)

3. **대한민국영문법령 공동활용 (`openlaw.klri.re.kr`)**
   - 조사 제외 영역(영문 번역본)이라 상세 조사는 생략.

### 법령 ID/MST 매핑 (조사 시점 2026-04-24, 현행)

law.go.kr 공식 본문 페이지에서 노출된 `lsiSeq` 기준. 법령명으로 재검색하면 최신 MST 가 바뀔 수 있으므로, 구현 시에는 `lsId` 로 조회 후 응답의 최신 `lsiSeq` 를 캐시하는 편이 안전하다.

| 법령명 | 약칭 | lsId (추정) | 현행 lsiSeq(MST) 예 | 비고 |
|---|---|---|---|---|
| 근로기준법 | 근기법 | `001769` | `265959` | 시행일 2025-10-23 참고 |
| 최저임금법 | 최임법 | — | `218303` (최근), 과거 `122697`, `72433`, `85945`, `2263` 등 다수 버전 확인 |  |
| 근로자퇴직급여 보장법 | 퇴직급여법 | — | `115301`, 과거 `66567` | 정확한 최신값은 `lsId` 로 재조회 권장 |
| 남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 | 남녀고용평등법 | — | `149932`, 과거 `105643`, `72458` | 법령명이 수차례 바뀐 이력 있음 |
| 기간제 및 단시간근로자 보호 등에 관한 법률 | 기간제법 | `010356` | `152050`, 과거 `204835` |  |
| 파견근로자 보호 등에 관한 법률 | 파견법 | — | `206723` |  |

> [!warning] 주의
> `lsId` 가 확인되지 않은 법령은 검색 API(`lawSearch.do?target=law&query={법령명}`) 로 법령명 검색 → 응답의 `법령ID`/`법령일련번호` 를 1회 캐시하는 전처리가 필요.

### 조문 단위 조회 가능 여부 — **가능**
- `target=lawjosub` 으로 조/항/호/목까지 직접 조회 가능.
- 샘플 호출: `http://www.law.go.kr/DRF/lawService.do?OC=test&target=lawjosub&type=XML&ID=001823&JO=000300&HANG=000100&HO=000200&MOK=다` (문서 샘플, 건축법 3조 1항 2호 다목).
- 응답에 `조문번호`, `조문내용`, `항번호`, `항내용`, `호번호`, `호내용`, `목번호`, `목내용`, `조문시행일자` 필드가 포함된다고 문서에 명시.
- 출처: [현행법령 본문 조항호목 조회 API](https://open.law.go.kr/LSO/openApi/guideResult.do?htmlName=lsNwJoListGuide)

### 개정 이력 추적 — **가능**
- **법령 연혁 목록**: `lawSearch.do?target=lsHistory`
  - 파라미터: `OC`, `target`, `type`, `query`, `display`(기본 20, 최대 100), `page`, `sort`(lasc/ldes/dasc/ddes), `efYd`(시행일 범위, 예: `20090101~20090130`), `ancNo`(공포번호 범위), `rrClsCd`(제정/개정/전부개정/폐지 구분).
- **일자별 조문 개정 이력 목록**, **조문별 변경 이력 목록** 도 별도 엔드포인트로 제공된다고 가이드 목록에서 확인.
- **특정 시점 조회**: 본문 조회에 `efYd=YYYYMMDD` 를 넘기면 해당 시행일 기준 스냅샷을 받을 수 있다. 판례가 참조한 구법을 다룰 때 유용.
- 출처: [OPEN API 가이드 목록](https://open.law.go.kr/LSO/openApi/guideList.do)

### Rate Limit / 쿼터

| 창구 | 명시적 수치 | 증량 조건 |
|---|---|---|
| `open.law.go.kr` (법제처 직접) | **수치 비명시**. "트래픽 초과로 시스템 부하 시 제한" 규정만 존재 | 사전 등록한 IP/도메인 외 호출 차단, 이용조건 위반시 정지 |
| `apis.data.go.kr` (공공데이터포털) | **개발 10,000건/일** | 활용사례 등록 후 운영계정 증량 신청 |

- 즉, 법제처 직접 창구는 명시 상한이 없는 대신 남용시 제한 가능성이 있어, **운영 환경 용도/트래픽 패턴을 먼저 알리고 신청하는 편이 안전**.
- 선행 사례: 한 사용자가 **약 2GB 분량의 법령·행정규칙 전체를 XML 로 무오류 내려받았다**고 기술. 순차 호출이면 적재 가능한 수준.
- 출처: [이용안내 - 국가법령정보 공동활용](https://open.law.go.kr/LSO/information/guide.do), [법령정보 크롤링 체험기 - 미래전략 연구소](https://mirae-lab.com/forecast/%EA%B5%AD%EA%B0%80%EB%B2%95%EB%A0%B9%EC%A0%95%EB%B3%B4%EC%84%BC%ED%84%B0-api%EB%A5%BC-%ED%99%9C%EC%9A%A9%ED%95%B4-%EB%B2%95%EB%A0%B9%EC%A0%95%EB%B3%B4-%ED%81%AC%EB%A1%A4%EB%A7%81/)

### API 키 발급 절차와 비용

| 단계 | 소요 | 비용 |
|---|---|---|
| 회원가입 (open.law.go.kr) | 즉시 | 무료 |
| OPEN API 활용신청 | 1~2일 내 승인 (담당자 심의, 실제로는 자동처리에 가까움) | 무료 |
| 공공데이터포털(data.go.kr) 활용신청 | 개발/운영 모두 자동승인 | 무료 |
| 호출 IP/도메인 등록 | 신청서 제출 시 | — |
| 문의 | 기술: 02-2109-6446, 사용신청: 044-200-6797 | — |

### 공공누리 라이선스

- **적용 유형: 제1유형** (출처표시). 법제처 국가법령정보 공유서비스가 명시.
- **허용 범위**: 영리 목적 이용, 2차적 저작물 제작, 변형·변경, 온·오프라인 재배포 **모두 허용**.
- **의무**:
  - 기관명, 작성연도, 저작물명, 작성자명, 공공누리 유형 표기.
  - 온라인 표시 가능한 경우 **출처 홈페이지 하이퍼링크 제공 필수**.
  - 공공기관과 특수 관계·후원관계처럼 오인되는 표시 금지.
- **출처표시 예시 문구**
  > 본 저작물은 "법제처"에서 개방한 "국가법령정보(공공누리 제1유형)"를 이용하였으며, 원문은 [국가법령정보센터](https://www.law.go.kr) 에서 확인할 수 있습니다.
- **위반시**: 이용허락 자동 종료, 즉시 이용 중단 의무. 계속 사용 시 저작권 침해로 민·형사 책임.
- **법령 원문 저작물성의 별도 언급**: "법령정보는 영리 목적 이용 포함 자유로운 활용 보장. 단 출처 미표기 시 형사처벌 대상." (open.law.go.kr 이용안내 문구)
- 출처: [공공누리 제1유형](https://www.kogl.or.kr/info/licenseType1.do), [이용안내 - 국가법령정보 공동활용](https://open.law.go.kr/LSO/information/guide.do)

### 실제 API 응답 예시

직접 `OC=test` 로 호출 시 **IP/도메인 미등록 인증 실패 XML** 만 돌아와 원본 스키마를 직접 확인하지는 못했다. 문서 기반으로 정리:

```text
# 법령 본문 조회 (XML) 예상 필드
<법령>
  <기본정보>
    <법령ID>001769</법령ID>
    <법령명_한글>근로기준법</법령명_한글>
    <공포일자>…</공포일자>
    <공포번호>…</공포번호>
    <시행일자>20251023</시행일자>
    <소관부처명>고용노동부</소관부처명>
  </기본정보>
  <조문>
    <조문단위 조문여부="조문">
      <조문번호>2</조문번호>
      <조문제목>정의</조문제목>
      <조문시행일자>…</조문시행일자>
      <조문내용>…</조문내용>
      <항>
        <항번호>1</항번호>
        <항내용>…</항내용>
        <호>
          <호번호>1</호번호>
          <호내용>…</호내용>
        </호>
      </항>
    </조문단위>
  </조문>
  <개정문>…</개정문>
</법령>
```

- 조항호목 조회(`target=lawjosub`) 응답에도 동일 계열 필드가 내려오며, `조문번호`·`항번호`·`호번호`·`목번호`·`목내용` 까지 중첩된다고 문서에 명시.
- 판례 본문(`target=prec`), 법령해석례(`target=expc`) 등 다른 대상도 동일한 DRF 패턴(`lawSearch.do` 로 목록, `lawService.do` 로 상세)으로 제공.
- 출처: [판례 본문 조회 API](https://open.law.go.kr/LSO/openApi/guideResult.do?htmlName=precInfoGuide)

### 관련 사례 / 레퍼런스

- [국가법령정보센터 API 크롤링 체험기 — 미래전략 연구소](https://mirae-lab.com/forecast/%EA%B5%AD%EA%B0%80%EB%B2%95%EB%A0%B9%EC%A0%95%EB%B3%B4%EC%84%BC%ED%84%B0-api%EB%A5%BC-%ED%99%9C%EC%9A%A9%ED%95%B4-%EB%B2%95%EB%A0%B9%EC%A0%95%EB%B3%B4-%ED%81%AC%EB%A1%A4%EB%A7%81/) — 승인 1~2일, 법령 목록 선조회 → 상세 호출 전략, 2GB 수준 벌크 다운로드 무오류, 별표·별지 수집은 별도 처리 필요.
- [판시요지·판례 크롤링 — 매크로마스터](https://macromaster.dev/blog/law-open-api) — DRF 기반 판례 수집 구조(WebFetch 차단으로 본문 직접 확인 실패, URL 만 인용).
- [법제처 국가법령정보 공유서비스 - 공공데이터포털](https://www.data.go.kr/data/15000115/openapi.do) — 10,000건/일 개발쿼터, 무료, 공공누리 1유형 명시.
- [legalize-kr/legalize-kr](https://github.com/legalize-kr/legalize-kr) — 법령을 Markdown + Git commit(공포일자) 로 버전 관리하는 오픈소스. 개정 이력 모델링 참고.

## 발견한 트레이드오프 (다음 분석 단계에서 결정할 것)

| 항목 | 법제처 직접 (`DRF`) | 공공데이터포털 프록시 |
|---|---|---|
| 쿼터 명시성 | 없음, 사후 제한 리스크 | 명시 10,000/일, 예측 가능 |
| 엔드포인트 다양성 | 법령·조항호목·연혁·해석례·판례·용어 등 풀셋 | 법령 검색 위주, 조문/연혁 여부 추가 확인 필요 |
| IP/도메인 등록 | 필수 | 상대적으로 덜 엄격 |
| 응답 포맷 | XML/JSON/HTML 선택 | XML 위주 |
| 별표·별지(이미지) | 별도 수집 필요 | 동일 제약 |
| 인증 방식 | OC (이메일ID, URL 노출) | serviceKey (URL 노출) |

## 열린 질문 (다음 분석 단계로)

- [ ] MVP 트래픽 추정: 6개 법령 × 평균 조문 수 × 캐시 TTL → 일일 필요 호출 수는? (프리셋 캐시 + 주기적 재검증 방식이 쿼터 안쪽인지)
- [ ] "현재 시행 버전만 보여줄지" vs "사건 발생일 기준 구법까지 보여줄지" 제품 요구사항 확정. 후자는 `efYd` 스냅샷·`lsHistory` 활용도가 크다.
- [ ] 개정 이벤트 감지 방법: 배치(매일/매주 `lsHistory` 폴링) vs 법령일련번호 변경 감지 트리거 중 어느 쪽?
- [ ] 별표·별지(표/양식) 데이터 필요 여부. 근로기준법 시행규칙 근처에 별표가 많음. MVP 범위에서 스킵 가능 여부 확인.
- [ ] OC 값이 URL 쿼리스트링에 노출되는 구조. 백엔드에서 프록시해 프론트/로그에 노출되지 않게 하는 수준이면 충분한가? → CLAUDE.md 의 "민감 정보 코드 직접 작성 금지" 원칙과 교차 검토.
- [ ] 공공데이터포털 경로가 `lawjosub`(조항호목) 까지 지원하는지 추가 확인. 지원 안 하면 법제처 직접 경로가 사실상 필수.
- [ ] 출처표시 UI 위치: CLAUDE.md 디스클레이머 문구와 별도로 "법제처 공공누리 1유형" 표기를 어디에 넣을지 (footer, 조문 상세 페이지, API 응답 메타).

## 참고 자료

- [OPEN API 활용가이드 - 국가법령정보 공동활용](https://open.law.go.kr/LSO/openApi/guideList.do)
- [이용안내 - 국가법령정보 공동활용](https://open.law.go.kr/LSO/information/guide.do)
- [현행법령 본문 조항호목 조회 API](https://open.law.go.kr/LSO/openApi/guideResult.do?htmlName=lsNwJoListGuide)
- [법제처 국가법령정보 공유서비스 - 공공데이터포털](https://www.data.go.kr/data/15000115/openapi.do)
- [공공누리 제1유형 - 공공누리 일반증서](https://www.kogl.or.kr/info/licenseType1.do)
- [국가법령정보센터 위키백과](https://ko.wikipedia.org/wiki/%EA%B5%AD%EA%B0%80%EB%B2%95%EB%A0%B9%EC%A0%95%EB%B3%B4%EC%84%BC%ED%84%B0)
- [법령정보 크롤링 — 미래전략 연구소](https://mirae-lab.com/forecast/%EA%B5%AD%EA%B0%80%EB%B2%95%EB%A0%B9%EC%A0%95%EB%B3%B4%EC%84%BC%ED%84%B0-api%EB%A5%BC-%ED%99%9C%EC%9A%A9%ED%95%B4-%EB%B2%95%EB%A0%B9%EC%A0%95%EB%B3%B4-%ED%81%AC%EB%A1%A4%EB%A7%81/)
- [legalize-kr/legalize-kr](https://github.com/legalize-kr/legalize-kr)
- [근로기준법 law.go.kr 본문](https://www.law.go.kr/LSW/lsInfoP.do?lsiSeq=265959)
- [최저임금법 law.go.kr 본문](https://www.law.go.kr/LSW/lsInfoP.do?lsiSeq=122697)
- [기간제법 law.go.kr 본문](https://www.law.go.kr/LSW/lsInfoP.do?urlMode=lsInfoP&lsId=010356)
- [파견법 law.go.kr 본문](https://www.law.go.kr/lsInfoP.do?lsiSeq=206723)
- [남녀고용평등법 law.go.kr 본문](https://www.law.go.kr/LSW/lsEfInfoP.do?lsiSeq=149932)
- [근로자퇴직급여 보장법 law.go.kr 본문](https://www.law.go.kr/lsInfoP.do?lsiSeq=115301)
