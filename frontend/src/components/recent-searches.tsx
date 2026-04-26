"use client";

import Link from "next/link";
import { useMemo, useSyncExternalStore } from "react";

import { Badge } from "@/components/ui/badge";
import {
  clearRecentSearches,
  getRecentSearchesServerSnapshot,
  getRecentSearchesSnapshot,
  parseRecentSnapshot,
  subscribeRecentSearches,
} from "@/lib/recent-searches";

/**
 * 최근 검색 칩 (max 5).
 *
 * useSyncExternalStore 로 localStorage 와 동기화 — 같은 탭에서 SearchBox 가
 * pushRecentSearch 한 직후에도 즉시 갱신된다. SSR 시 빈 배열이라 hydration
 * mismatch 없음.
 */
export function RecentSearches() {
  const raw = useSyncExternalStore(
    subscribeRecentSearches,
    getRecentSearchesSnapshot,
    getRecentSearchesServerSnapshot,
  );
  const recent = useMemo(() => parseRecentSnapshot(raw), [raw]);

  if (recent.length === 0) return null;

  return (
    <div className="flex flex-wrap items-center gap-2">
      <span className="text-xs text-muted-foreground">최근 검색</span>
      {recent.map((q) => (
        <Link
          key={q}
          href={`/search?q=${encodeURIComponent(q)}`}
          className="inline-flex"
        >
          <Badge
            variant="outline"
            className="h-7 cursor-pointer px-2.5 text-xs hover:bg-muted"
          >
            {q}
          </Badge>
        </Link>
      ))}
      <button
        type="button"
        onClick={clearRecentSearches}
        className="text-xs text-muted-foreground underline-offset-2 hover:underline"
      >
        지우기
      </button>
    </div>
  );
}
