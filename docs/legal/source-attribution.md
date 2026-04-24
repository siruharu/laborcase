# 데이터 출처 표시

laborcase 는 다음 공공 데이터를 이용합니다. 이용 시 이 페이지의 지침을 따릅니다.

## 국가법령정보센터 (법제처)

- **라이선스**: 공공누리 제1유형 — 출처표시
- **허용**: 영리 이용, 2차 저작물 제작, 변형·재배포
- **의무**: 출처표시, 온라인에서는 원문 하이퍼링크 제공
- **금지**: 공공기관과 특수관계·후원관계를 오인하게 하는 표시, 원본 의도를 훼손하는 수정

### 표준 출처 문구

UI 조문 상세 페이지·API 응답 메타·footer 에 다음 문구를 포함합니다.

> 본 저작물은 "법제처" 가 공공누리 제1유형으로 개방한 "국가법령정보(법령 본문)"를 이용하였으며, 해당 저작물은 [국가법령정보센터](https://www.law.go.kr) 에서 확인할 수 있습니다.

조문 단위 렌더링 시에는 해당 조문의 직접 링크(`https://www.law.go.kr/lsInfoP.do?lsiSeq=...`) 를 동반 표시합니다.

### 금지 표현

다음 문구는 서비스 어디에도 사용하지 않습니다.

- "법제처 공식 파트너"
- "법제처 인증"
- "법제처 제휴"

## API 응답의 출처 메타

`LawController` 가 내려주는 모든 조문 응답 JSON 은 다음 필드를 포함합니다 (Task 9 에서 구현, Task 11 에서 freshness 추가).

```json
{
  "data": { ... },
  "source": {
    "provider": "법제처 국가법령정보센터",
    "license": "KOGL-1",
    "url": "https://www.law.go.kr/lsInfoP.do?lsiSeq=...",
    "retrievedAt": "2026-04-24T10:00:00Z"
  },
  "freshness": {
    "lastSyncedAt": "2026-04-24T06:30:05Z",
    "stale": false,
    "staleThresholdHours": 48
  },
  "disclaimer": "본 정보는 공개된 판례 및 법령에 기반한 참고 자료이며, 법률 자문이 아닙니다. 구체적 사건은 반드시 변호사·노무사와 상담하세요."
}
```

`freshness.stale == true` 일 때의 프론트 배너 요건은 [ADR-0002](../decisions/adr-0002-stale-data-banner.md) 를 따릅니다.

## 프론트 렌더링 지침 (별도 플랜에서 상세)

- 조문 상세 페이지 상단에 `source.url` 로의 "원문 보기" 링크 필수.
- 페이지 footer 에 "본 서비스는 법제처 국가법령정보(공공누리 제1유형)를 이용합니다." 고정 표시.
- `freshness.stale == true` 이면 dismiss 불가 노란색 배너 상단 고정.
- 구현 세부는 후속 프론트엔드 플랜 (`2026-04-??_frontend-law-view.md`) 참조 예정.

## 참고

- [공공누리 제1유형 (한국저작권위원회)](https://www.kogl.or.kr/info/licenseType1.do)
- [국가법령정보 공동활용 이용안내](https://open.law.go.kr/LSO/information/guide.do)
