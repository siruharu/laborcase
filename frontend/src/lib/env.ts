/**
 * NEXT_PUBLIC_* 환경변수는 빌드 타임에 인라인되므로,
 * 누락 시 런타임에 호출하는 컴포넌트가 의미 없는 fetch 실패를 내기보다
 * 모듈 로드 시점에 명시적으로 throw 하는 편이 디버깅 비용이 작다.
 */

function required(name: string, value: string | undefined): string {
  if (!value || value.trim().length === 0) {
    throw new Error(
      `Missing required environment variable: ${name}. ` +
        `Set it in .env.local (see .env.example) or your runtime config.`,
    );
  }
  return value.trim();
}

export const env = {
  apiBaseUrl: required(
    "NEXT_PUBLIC_API_BASE_URL",
    process.env.NEXT_PUBLIC_API_BASE_URL,
  ).replace(/\/$/, ""),
} as const;
