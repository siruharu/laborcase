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

/**
 * 조 + 가지 + 항 + 호 → 한국어 조문 번호 표기.
 * 예: (23, null, 1, null) → "제23조 ①"
 *     (23, 2, null, null) → "제23조의2"
 *     (28, null, 1, 3)    → "제28조 ① 3."
 */
export function formatArticleNumber(
  jo: number,
  joBranch?: number,
  hang?: number,
  ho?: number,
): string {
  const parts: string[] = [];
  parts.push(joBranch ? `제${jo}조의${joBranch}` : `제${jo}조`);
  if (hang) parts.push(hangToCircled(hang));
  if (ho) parts.push(`${ho}.`);
  return parts.join(" ");
}
