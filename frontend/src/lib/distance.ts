/**
 * pgvector cosine distance → 사용자에게 보여줄 관련도 라벨.
 * 임계값 출처: docs/research/distance-thresholds.md (FT-Task 0 PoC).
 *
 * - d < 0.65         → "높음" (teal-600 채움)
 * - 0.65 ≤ d < 0.72  → "보통" (zinc-300 outline)
 * - d ≥ 0.72         → null (라벨 미표시, 정렬에만 사용)
 *
 * 단정 표현 회피 (CLAUDE.md §법적 제약) 차원에서 "관련도" 라는 정성적
 * 표현만 노출하고 distance 수치는 UI 에 직접 보여주지 않는다.
 */

export type RelevanceLabel = "높음" | "보통" | null;

export const HIGH_RELEVANCE_THRESHOLD = 0.65;
export const MEDIUM_RELEVANCE_THRESHOLD = 0.72;

export function distanceToLabel(d: number): RelevanceLabel {
  if (d < HIGH_RELEVANCE_THRESHOLD) return "높음";
  if (d < MEDIUM_RELEVANCE_THRESHOLD) return "보통";
  return null;
}
