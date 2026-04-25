# Distance → 관련도 라벨 임계값 캘리브레이션

- **작성일**: 2026-04-25
- **방법**: 10개 자연어 쿼리 → Upstage `solar-embedding-1-large-query` → pgvector cosine distance → top 10 결과 정성 평가
- **출처 스크립트**: `scripts/distance-poc.sh`
- **원본 결과**: `/tmp/distance-poc.txt` (로컬, 미커밋)
- **계기**: 프론트 분석 §P2-c, 플랜 Front-Task 0
- **참고 ADR**: [ADR-0003 임베딩 모델](../decisions/adr-0003-embedding-model.md)

## 결과 요약

| # | 쿼리 | 매칭 도메인 | top-1 distance | top-5 matching/related |
|---|---|---|---|---|
| 1 | 부당하게 해고당했다 어떻게 해야 하나 | 근기법 §28 (구제신청) | 0.6095 | 5/5 |
| 2 | 월급이 한 달째 안 들어와요 | 근기법 §43 (임금체불) | 0.6784 | 5/5 |
| 3 | 출산휴가 중에 잘렸어요 | 근기법 §74 + 남녀고용평등법 §18 | 0.5984 | 5/5 |
| 4 | 최저임금 못 받았는데 신고할 수 있나 | 최임법 §6 | 0.6672 | 4/5 |
| 5 | 회사가 갑자기 부서를 옮기래요 거부할 수 있나 | (rank 9 에서 근기법 §23) | 0.6822 | **0/5** ⚠️ |
| 6 | 퇴직금을 안 줘요 | 퇴직급여법 §4, §9 | 0.5965 | 5/5 |
| 7 | 야근수당 못 받았어요 | 근기법 §56 (가산수당) | 0.6567 | 5/5 |
| 8 | 비정규직인데 계약 갱신 안 해줘요 | 기간제법 §4, §5, §16 | 0.6077 | 5/5 |
| 9 | 성희롱 신고했는데 회사가 보복했어요 | 남녀고용평등법 §14, §37 | 0.5966 | 5/5 |
| 10 | 파견직인데 정규직 전환 가능한가 | 파견법 §6 | 0.6272 | 5/5 |

**합계**: 9/10 쿼리에서 top-5 의 4건 이상이 matching/related. 정성 기준 충족.

**약점 (Q5)**: "부서를 옮기래요" 가 법령 용어 "전직" 과 매칭 약함. 근기법 제23조의 "전직"이 부당해고등에 포함되지만 임베딩 공간에서 거리가 멀게 나옴. → 베타 라벨 + 후속 검색 품질 개선(쿼리 확장 / 동의어 사전).

## Distance 분포

| 평가 | 관측 distance 범위 |
|---|---|
| MATCHING (직접 답) | 0.5965 ~ 0.6784 |
| RELATED (같은 영역) | 0.6147 ~ 0.7003 |
| UNRELATED (Q5 등) | 0.6822 ~ 0.7327 |

**겹치는 구간**: 0.68 ~ 0.70 (matching 과 unrelated 가 섞이는 영역).

## 결정한 임계값

```
distance < 0.65         → 라벨: "관련도 높음" (teal-600)
0.65 ≤ distance < 0.72  → 라벨: "관련도 보통" (zinc-300)
distance ≥ 0.72         → 라벨 미표시 (정렬에는 사용)
```

### 임계값 적용 시뮬레이션

| 쿼리 | 高 | 中 | 미표시 (top 5 기준) |
|---|---|---|---|
| Q1 부당해고 | 4 | 1 | 0 |
| Q2 임금체불 | 0 | 5 | 0 |
| Q3 출산휴가 | 5 | 0 | 0 |
| Q4 최저임금 | 0 | 5 | 0 |
| Q5 부서이동 | 0 | 5 | 0 (top1 0.68 → 中) |
| Q6 퇴직금 | 5 | 0 | 0 |
| Q7 야근수당 | 0 | 5 | 0 |
| Q8 비정규직 | 4 | 1 | 0 |
| Q9 성희롱 | 5 | 0 | 0 |
| Q10 파견 | 5 | 0 | 0 |

> Q5 가 모두 "中" 으로 표시되는 점은 **부정확**하나, 사용자가 어차피 결과를 보고 판단해야 하므로 큰 손해는 아님. Q5 같은 케이스는 베타 라벨 + 검색 팁 ("법률 용어로 다시 검색해보세요") 으로 보완.

## 코드 적용 위치

`frontend/src/lib/distance.ts`:

```ts
export type RelevanceLabel = '높음' | '보통' | null;

export function distanceToLabel(d: number): RelevanceLabel {
  if (d < 0.65) return '높음';
  if (d < 0.72) return '보통';
  return null; // 정렬에만 사용
}

export const HIGH_RELEVANCE_THRESHOLD = 0.65;
export const MEDIUM_RELEVANCE_THRESHOLD = 0.72;
```

`docs/decisions/adr-0003-embedding-model.md` §후속 항목에 임계값 결정 사실을 반영하면 깔끔.

## 후속

- [ ] 베타 기간 동안 사용자가 입력한 실제 쿼리 + top-5 distance 분포 수집 (사용 데이터 분석).
- [ ] Q5 같은 "비유적 표현" 케이스용으로 동의어 사전 (전직 ↔ 부서 이동 ↔ 부서 변경) 검토.
- [ ] 1년 후 임계값 재캘리브레이션.

## 참고

- 프론트 분석: [`02_Analysis/2026-04-25_frontend-nextjs-legal-search-ux.md`](../analysis/2026-04-25_frontend-nextjs-legal-search-ux.md)
- 플랜 Task 0: [`03_Plan/2026-04-25_frontend-nextjs-legal-search-ux.md`](../plans/2026-04-25_frontend-nextjs-legal-search-ux.md)
