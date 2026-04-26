/**
 * laborcase 백엔드 호출 래퍼.
 *
 * 모든 응답이 ApiResponse<T> 로 감싸져 오므로 unwrap 하지 않고 그대로 반환한다.
 * 페이지가 source / freshness 까지 필요로 하기 때문 (출처 표시 + stale 배너).
 *
 * 캐시 정책:
 * - 검색 (`searchArticles`): no-store. 사용자 쿼리는 매번 다르고 캐시 가치 낮음.
 * - 목록 (`listLaws`), 조문 (`getLawArticles`): revalidate 60s. 거의 안 바뀜.
 */

import { env } from "./env";
import type {
  ApiResponse,
  ArticleListResponse,
  ArticleSearchResponse,
  LawSummary,
} from "@/types/api";

async function apiFetch<T>(
  path: string,
  init: RequestInit = {},
): Promise<ApiResponse<T>> {
  const url = `${env.apiBaseUrl}${path}`;
  const res = await fetch(url, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
      ...(init.headers ?? {}),
    },
  });

  if (!res.ok) {
    const body = await res.text().catch(() => "");
    throw new ApiError(res.status, path, body.slice(0, 500));
  }

  return (await res.json()) as ApiResponse<T>;
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly path: string,
    public readonly body: string,
  ) {
    super(`laborcase API ${status} for ${path}`);
    this.name = "ApiError";
  }
}

export async function searchArticles(
  query: string,
  limit?: number,
): Promise<ApiResponse<ArticleSearchResponse>> {
  return apiFetch<ArticleSearchResponse>("/api/v1/articles/search", {
    method: "POST",
    body: JSON.stringify({ query, limit }),
    cache: "no-store",
  });
}

export async function listLaws(): Promise<ApiResponse<LawSummary[]>> {
  return apiFetch<LawSummary[]>("/api/v1/laws", {
    next: { revalidate: 60 },
  });
}

export async function getLawArticles(
  key: string,
  jo?: number,
  hang?: number,
  ho?: number,
): Promise<ApiResponse<ArticleListResponse>> {
  const params = new URLSearchParams();
  if (jo !== undefined) params.set("jo", String(jo));
  if (hang !== undefined) params.set("hang", String(hang));
  if (ho !== undefined) params.set("ho", String(ho));
  const qs = params.toString();
  const path = `/api/v1/laws/${encodeURIComponent(key)}/articles${qs ? `?${qs}` : ""}`;
  return apiFetch<ArticleListResponse>(path, {
    next: { revalidate: 60 },
  });
}
