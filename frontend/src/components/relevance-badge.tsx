import { Badge } from "@/components/ui/badge";
import { distanceToLabel } from "@/lib/distance";

/**
 * 검색 결과 카드의 관련도 라벨.
 *
 * - "높음": brand 색 (teal-600) 채움. 시각적 강조 1순위.
 * - "보통": outline (zinc-300). 강조 2순위.
 * - null: 렌더하지 않음. 결과 정렬에는 영향 없음.
 *
 * distance 수치는 의도적으로 노출하지 않는다 — CLAUDE.md §법적 제약 의 단정
 * 표현 회피, 그리고 임계값 PoC (research/distance-thresholds.md) 가 정성
 * 라벨로의 변환을 전제로 캘리브레이션됐다.
 */
interface Props {
  distance: number;
}

export function RelevanceBadge({ distance }: Props) {
  const label = distanceToLabel(distance);
  if (label === null) return null;

  if (label === "높음") {
    return (
      <Badge
        className="border-transparent bg-[var(--color-brand)] text-[var(--color-brand-foreground)]"
        aria-label="관련도 높음"
      >
        관련도 높음
      </Badge>
    );
  }

  return (
    <Badge variant="outline" aria-label="관련도 보통">
      관련도 보통
    </Badge>
  );
}
