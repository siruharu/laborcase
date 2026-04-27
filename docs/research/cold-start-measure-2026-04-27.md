# Cloud Run API 콜드 스타트 측정 — 2026-04-27 (1차)

- **방법**: prod Cloud Run service `laborcase-api` (asia-northeast3, vCPU 1 / RAM 1Gi / startup CPU boost / min=0) 에 직접 curl 으로 latency 측정.
- **계기**: 분석 §검증필요 / DT-Task 5.

## 결과

### Warm requests (배포 직후, 인스턴스 살아있음)
```
warm 1: 0.090236s
warm 2: 0.088197s
warm 3: 0.090021s
warm 4: 0.092966s
warm 5: 0.083919s
```
→ **P95 ≤ 95ms, 매우 일관**.

### Cold start 시도 (6분 idle 후)
```
cold-attempt-1: 0.090187s
cold-attempt-2: 0.095808s
cold-attempt-3: 0.082838s
```
→ **여전히 warm latency**. 6분으로는 Cloud Run 이 instance 를 회수하지 않음.

## 해석

- 배포 직후의 첫 사용자 요청은 startup probe 가 통과한 후 들어오므로 이미 JVM warm-up 완료 상태. probe 자체에 startup_cpu_boost 적용된 채 ~10초 안에 ready 응답.
- Cloud Run 의 instance idle timeout 은 default ~15분 추정. 6분으로는 부족.
- 진짜 콜드 스타트 측정은 별도 follow-up 으로:
  - 옵션 A: 30분 idle 후 재측정.
  - 옵션 B: `gcloud run services update --no-traffic ... + 새 revision 배포` 으로 강제 cold.
  - 옵션 C: 첫 배포 직후의 startup probe 응답 시간을 Cloud Logging 에서 직접 본다 (이번 측정에서 첫 revision 시작이 ~10s 였음 — log 에서 확인 가능).

## 잠정 결정

분석 §P1-c 의 `min=0` 결정 **유지**. 이유:
1. warm latency ~85-95ms 는 우수.
2. 진짜 콜드 스타트 latency 가 5-10s 라도 사용자 첫 요청은 page load + RSC fetch 흐름 내에서 흡수됨 (FT-Task 7 의 Skeleton UI 가 1초 이상 가시화 처리).
3. 비용 절감 가치 > 콜드 스타트 UX 페널티 (DAU 수십 명 가정).

**트리거 (재검토)**:
- DAU 100+ 도달
- P95 first-request latency > 5s 가시화
- 사용자 피드백에서 "처음 검색이 느리다" 반복 보고

위 트리거 시 `terraform apply -var api_min_instances=1` (~$50/월).

## 부록 — 첫 배포 / 검색 결과 캡처

### `GET /api/v1/laws`
- HTTP 200, time 263ms (warm)
- KOGL-1 source, freshness.stale=false
- 6 법령: 근기법 / 최임법 / 파견법 / 기간제법 / 퇴직급여법 / 남녀고용평등법

### `POST /api/v1/articles/search` query="부당하게 해고당했다 어떻게 해야 하나"
```
hits: 5
  근기법 제28조 ① distance=0.6095   (구제신청)
  근기법 제28조 (header) distance=0.6315
  근기법 제30조 ① distance=0.6447   (구제명령)
  근기법 제28조 ② distance=0.6470
  근기법 제23조 ① distance=0.6585   (해고 등의 제한)
```
→ 라벨 분포: 모두 < 0.65 또는 < 0.72 → **"관련도 높음" 1건 + "관련도 보통" 4건**. PoC `distance-thresholds.md` 의 Q1 결과와 일치.

## Follow-up

- [ ] 30분 이상 idle 후 진짜 cold-start 1회 더 측정.
- [ ] Cloud Logging 의 startup probe 응답 시간으로 JVM init 시간 확인.
- [ ] DAU 100+ 도달 시점에 min=1 vs min=0 비용/UX 재평가.

## 참고
- 분석: [GCP 프로덕션 배포 분석 §P1-c](../../{Vault}/10_Projects/laborcase/02_Analysis/2026-04-27_gcp-prod-deploy.md)
- 임계값 PoC: [distance-thresholds.md](./distance-thresholds.md)
- API URL: `https://laborcase-api-mxq42pqgaa-du.a.run.app`
