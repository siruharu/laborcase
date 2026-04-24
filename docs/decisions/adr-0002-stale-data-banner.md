# ADR-0002: "데이터가 오래되었습니다" 배너 + freshness 메타

- **Date**: 2026-04-24
- **Status**: Accepted
- **Context sources**: 플랜 [2026-04-24_national-law-open-api](../plans/2026-04-24_national-law-open-api.md) §R2, §P5

## 컨텍스트

laborcase 의 법령 데이터는 **일 1회 delta-sync** 로 최신화된다. 실패가 누적되면 UI 가 마지막 성공 시점 이후의 개정을 반영하지 못한 채 표시된다. CLAUDE.md §표현 제약 + "잘못된 법 조항 언급 0건" 목표상 **오래된 데이터임을 사용자에게 명시적으로 알리는** 가드가 필요하다.

## 결정

1. API 응답에 `freshness` 객체를 포함한다.
   ```json
   {
     "data": { ... },
     "source": { ... },
     "freshness": {
       "lastSyncedAt": "2026-04-23T18:00:00Z",
       "stale": false,
       "staleThresholdHours": 48
     },
     "disclaimer": "..."
   }
   ```

2. `stale = true` 판정 기준:
   - `sync_log` 에 `status = 'SUCCESS'` 가 하나도 없거나,
   - 최근 SUCCESS 의 `finished_at` 이 현재 시각 대비 `staleThresholdHours` 이상 이전인 경우.

3. 초기값: `staleThresholdHours = 48`. 환경변수 `LABORCASE_STALE_HOURS` 로 덮어쓸 수 있다.

4. **프론트 가이드**:
   - `freshness.stale == true` 이면 페이지 상단(또는 조문 상세 영역 바로 위)에 경고 배너 고정.
   - 문구 예시: "현재 노출된 법령 정보는 {lastSyncedAt} 이후 최신화되지 못했습니다. 반드시 [법제처 국가법령정보센터](https://www.law.go.kr) 원문과 대조하세요."
   - 배너는 **dismiss 불가** — 오류 가림 장치가 되지 않도록.

5. 배너 톤은 일반 에러 메시지 스타일이 아닌 **정보 제공 톤**(노란색 배경, 경고 아이콘). CLAUDE.md 법적 표현 제약과 일관.

## 대안

- **서버 측 강한 차단** (stale 이면 API 자체를 5xx 로): 사용자가 아무 정보도 못 봄 → 정보 제공 서비스 목표와 어긋남. 기각.
- **프론트에서만 계산** (`Date.now() - lastSyncedAt`): 시계 동기화 가정이 필요. 서버 시계 기준으로 서버가 내려주는 편이 견고.
- **배너 대신 toast**: 사용자가 놓칠 수 있음. 배너 + dismiss 불가 조합이 법적 표현 제약에 더 강함.

## 결과

- 프론트 구현 비용이 작음 (1개 배너 컴포넌트 + 단순 조건).
- 운영자가 `sync_log` 혹은 Sentry 대신 `freshness.stale=true` 응답만 봐도 사고 감지 가능.
- Task 11 에서 MockMvc + dev-postgres 통합 테스트 5건으로 보호 중.

## 후속

- 프론트 Phase 1 에서 배너 컴포넌트 구현.
- `staleThresholdHours` 값을 트래픽 패턴 관찰 후 24h 등으로 조정 검토.
- `freshness.perLaw` 같이 법령 단위 stale 표시 (현재는 전 DB 1개 값). 필요 시 `law_version.fetched_at` 활용.
