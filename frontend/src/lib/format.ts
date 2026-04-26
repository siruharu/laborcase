/**
 * 한국어 날짜 / 항·호 표시 변환.
 * FT-Task 4 의 stale 배너, detail-box, FT-Task 8 의 article-tree 가 공유한다.
 */

const koDateFormatter = new Intl.DateTimeFormat("ko-KR", {
  timeZone: "Asia/Seoul",
  year: "numeric",
  month: "long",
  day: "numeric",
});

const koDateTimeFormatter = new Intl.DateTimeFormat("ko-KR", {
  timeZone: "Asia/Seoul",
  year: "numeric",
  month: "long",
  day: "numeric",
  hour: "2-digit",
  minute: "2-digit",
});

/** "2025-10-23" → "2025년 10월 23일" */
export function formatKoDate(iso: string): string {
  return koDateFormatter.format(new Date(iso));
}

/** "2026-04-23T18:00:00Z" → "2026년 4월 24일 03:00" (KST) */
export function formatKoDateTime(iso: string): string {
  return koDateTimeFormatter.format(new Date(iso));
}

/** 항 번호 1..20 → ① ... ⑳, 그 외는 평문. */
export function hangToCircled(n: number): string {
  if (n < 1 || n > 20) return String(n);
  return String.fromCharCode(0x2460 + n - 1);
}
