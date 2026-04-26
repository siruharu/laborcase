import { formatKoDateTime } from "@/lib/format";
import type { Freshness } from "@/types/api";

/**
 * ADR-0002 — `freshness.stale === true` 일 때 dismiss 불가 amber 배너.
 *
 * stale 이 false 이거나 freshness 가 없으면 아무것도 렌더링하지 않는다.
 * 페이지(결과 / 상세)가 응답을 받은 직후 최상단에 배치한다.
 */
interface Props {
  freshness?: Freshness;
}

export function StaleBanner({ freshness }: Props) {
  if (!freshness?.stale) return null;

  const lastSyncedText = freshness.lastSyncedAt
    ? formatKoDateTime(freshness.lastSyncedAt)
    : "마지막 동기화 시점 미상";

  return (
    <div
      role="alert"
      className="rounded-md border border-[var(--color-stale-border)] bg-[var(--color-stale-bg)] px-4 py-3 text-sm text-[var(--color-stale-foreground)]"
    >
      <p>
        <span aria-hidden className="mr-1.5">⚠</span>
        현재 노출된 법령 정보는 <strong className="font-semibold">{lastSyncedText}</strong>{" "}
        이후 최신화되지 못했습니다. 반드시{" "}
        <a
          href="https://www.law.go.kr"
          target="_blank"
          rel="noopener noreferrer"
          className="underline underline-offset-2"
        >
          법제처 국가법령정보센터
        </a>
        의 원문과 대조하세요.
      </p>
    </div>
  );
}
