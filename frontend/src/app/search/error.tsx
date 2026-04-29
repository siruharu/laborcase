"use client";

import { Button } from "@/components/ui/button";

/**
 * /search 의 RSC 가 throw 한 경우 표시되는 fallback.
 * 백엔드 5xx, Upstage 503 (UPSTAGE_API_KEY 누락) 등 일시적 에러를 사용자
 * 친화적으로 노출. CLAUDE.md §법적 표현 제약 — 단정적 에러 메시지 회피,
 * "일시적 문제" 톤 유지.
 */
export default function Error({ reset }: { error: Error; reset: () => void }) {
  return (
    <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col items-start gap-4 px-6 py-14">
      <h1 className="text-xl font-semibold text-foreground">
        검색 중 일시적인 문제가 발생했습니다
      </h1>
      <p className="text-sm text-muted-foreground">
        잠시 후 다시 시도해 주세요. 같은 오류가 반복되면 페이지를 새로
        고쳐주세요.
      </p>
      <Button type="button" onClick={reset}>
        다시 시도
      </Button>
    </main>
  );
}
