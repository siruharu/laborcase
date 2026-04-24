# ADR-0003: 조문 임베딩 모델 = Upstage solar-embedding-1-large

- **Date**: 2026-04-24
- **Status**: Accepted
- **Context sources**: 분석 §P3, §R1, Task 8 가격 리서치

## 컨텍스트

laborcase 는 사용자의 자연어 상황 기술을 조문 단위로 의미 검색해야 한다 (근기법 제23조, 제43조 등). 한국어 법률 텍스트 기반 RAG 에서 임베딩 품질이 검색 top-N 재현율을 결정한다. 분석 §P3 에서 후보로 잡았던 모델:

- **OpenAI `text-embedding-3-small`** — 1536 dims, $0.020/1M tokens, 범용 다국어
- **Upstage `solar-embedding-1-large-*`** — 4096 dims, $0.10/1M tokens, 한국어 최적

## 결정

**Upstage `solar-embedding-1-large-passage` / `-query` 듀얼 모델** 을 채택한다.

### 선택 이유

1. **한국어 품질 우위**: Upstage 자체 벤치마크에서 Ko-miracl +7.84pt, MTEB +4.91pt. 한국어 법률 도메인에서 OpenAI text-embedding-3-large 대비 **동등 이상** 이라 주장.
2. **Query/Passage 분리 설계**: 사용자 쿼리와 조문을 각각 다른 모델로 임베딩해 같은 vector space 로 검색. 법령 검색 시나리오의 비대칭성 (짧은 쿼리 vs 장문 조문) 에 잘 맞음.
3. **비용 무시 가능**: MVP 트래픽 (DAU 수백명 × 5 쿼리/일) 기준 월 약 1,000~1,500원. 5배 비싸지만 절대액이 작아 품질 업그레이드 비용으로 타당.
4. **경쟁사 차별화 힌트**: 공개 레포에서 Upstage Solar 사용이 드러나면 "한국어·법률 특화" 시그널.

### 선택하지 않은 이유

- **OpenAI text-embedding-3-small**: 가격이 1/5 지만 Ko-miracl 벤치 열세 + query/passage 분리 없음. MVP 제품 가치의 핵심이 "한국어 조문 검색 정확도" 이므로 5배 가격이 아깝지 않다.
- **OpenAI text-embedding-3-large (3072 dims)**: 가격 $0.13/1M tokens 로 Upstage 와 유사하지만 한국어 벤치 여전히 열세.

## 결과

### 차원 불일치 — V3 마이그레이션 필요

V1 스키마의 `article_embedding.vector vector(1536)` 은 OpenAI text-embedding-3-small 전제였음. Upstage 는 **4096 dims** 이므로 스키마 변경이 필요.

**V3 마이그레이션 내용**:
1. `DROP INDEX article_embedding_ivfflat_idx` — ivfflat 은 pgvector 0.8 에서 ~2000 dims 한계.
2. `ALTER TABLE article_embedding ALTER COLUMN vector TYPE vector(4096)`.
3. `model` 컬럼 기본값을 `upstage:solar-embedding-1-large-passage` 로.
4. **인덱스 재생성 보류** — pgvector 0.8 은 `vector` 타입에 HNSW/ivfflat 모두 2000 dims 이상을 거부. MVP 의 ~1,500 rows 에서는 seq scan 이 10ms 미만. 행 수 증가 시 별도 V4 로 `halfvec(4096)` + HNSW 전환.

Task 8 전이라 `article_embedding` 은 비어있음 → 데이터 손실 없음.

### API 호출 구조

- **문서 임베딩** (FullSync/Delta 에서 조문 insert 후 실행):
  ```
  POST https://api.upstage.ai/v1/embeddings
  Authorization: Bearer ${UPSTAGE_API_KEY}
  body: { "input": ["<조문내용>"], "model": "solar-embedding-1-large-passage" }
  ```
- **쿼리 임베딩** (런타임, 사용자 검색어마다):
  ```
  body: { "input": "<사용자 상황>", "model": "solar-embedding-1-large-query" }
  ```

### 비용 제어

환경변수 `EMBEDDING_DAILY_LIMIT_USD` (기본 1.0) 로 하루 상한. 잡이 상한 초과 감지 시 조기 종료 + 경고 로그. Sentry 이벤트로 알림.

## 후속

- [ ] Upstage 가 dimension 축소 (Matryoshka) 를 지원하게 되면 재평가. 1536 dims 로 축소 가능하면 HNSW 대신 ivfflat 유지 가능 + 메모리 절감.
- [ ] 검색 품질 실측 (10~20개 사용자 쿼리로 top-5 재현율 수동 평가) 후 모델 유지/교체 결정.
- [ ] 임베딩 모델 교체 비용: `article_embedding` 테이블 재구축. FullSync 수동 1회 재실행. 소수 초 소요.

## 관련

- [plan 2026-04-24_national-law-open-api](../plans/2026-04-24_national-law-open-api.md) §P3, Task 8
- [ADR-0001 repo split](./adr-0001-repo-split.md)
- [ADR-0002 stale data banner](./adr-0002-stale-data-banner.md)
