/**
 * 최근 검색어 5개를 localStorage 에 저장.
 * SearchBox 가 push, RecentSearches 가 useSyncExternalStore 로 read.
 *
 * 키: `laborcase.recent-searches`. 값: JSON string array (최신이 [0]).
 *
 * 같은 탭에서 storage 변경은 'storage' event 가 자동으로 발화하지 않으므로
 * push/clear 시 직접 dispatch 한다. 그래야 같은 페이지의 RecentSearches 가
 * 즉시 갱신된다.
 */

const KEY = "laborcase.recent-searches";
const MAX = 5;

/** snapshot 캐시 — useSyncExternalStore 의 referential equality 보장. */
let cachedRaw: string = "[]";

export function readRecentSearches(): string[] {
  if (typeof window === "undefined") return [];
  return parse(window.localStorage.getItem(KEY) ?? "[]");
}

export function pushRecentSearch(query: string): void {
  if (typeof window === "undefined") return;
  const trimmed = query.trim();
  if (trimmed.length === 0) return;

  const prev = readRecentSearches();
  const next = [trimmed, ...prev.filter((q) => q !== trimmed)].slice(0, MAX);
  const json = JSON.stringify(next);
  window.localStorage.setItem(KEY, json);
  notify(json);
}

export function clearRecentSearches(): void {
  if (typeof window === "undefined") return;
  window.localStorage.removeItem(KEY);
  notify("[]");
}

/** useSyncExternalStore subscribe 헬퍼. */
export function subscribeRecentSearches(callback: () => void): () => void {
  if (typeof window === "undefined") return () => {};
  const handler = (e: StorageEvent) => {
    if (e.key === null || e.key === KEY) callback();
  };
  window.addEventListener("storage", handler);
  return () => window.removeEventListener("storage", handler);
}

/** useSyncExternalStore getSnapshot — string 안정성 위해 cache. */
export function getRecentSearchesSnapshot(): string {
  if (typeof window === "undefined") return "[]";
  const raw = window.localStorage.getItem(KEY) ?? "[]";
  if (raw !== cachedRaw) cachedRaw = raw;
  return cachedRaw;
}

export function getRecentSearchesServerSnapshot(): string {
  return "[]";
}

export function parseRecentSnapshot(raw: string): string[] {
  return parse(raw);
}

function parse(raw: string): string[] {
  try {
    const v: unknown = JSON.parse(raw);
    if (!Array.isArray(v)) return [];
    return v.filter((x): x is string => typeof x === "string").slice(0, MAX);
  } catch {
    return [];
  }
}

function notify(newValue: string) {
  window.dispatchEvent(
    new StorageEvent("storage", {
      key: KEY,
      newValue,
    }),
  );
}
