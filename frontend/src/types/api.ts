/**
 * laborcase 백엔드 응답 타입.
 *
 * Kotlin DTO 와 1:1 대응 (api/src/main/kotlin/kr/laborcase/law/web/{ApiResponse,LawReadDto,LawSearchDto,SyncFreshness}.kt).
 * 백엔드는 모든 응답을 {@link ApiResponse} 로 감싸 source(공공누리 출처표시)
 * + disclaimer(CLAUDE.md §UI 필수 요소) 를 항상 포함시킨다.
 *
 * Jackson @JsonInclude(NON_NULL) 때문에 nullable 필드는 JSON 에서
 * 키가 통째로 빠질 수 있어 optional (`?:`) 로 표현한다.
 *
 * Date 직렬화 형식:
 * - Instant → ISO-8601 문자열 (예: "2026-04-25T18:00:00Z")
 * - LocalDate → ISO-8601 문자열 (예: "2025-10-23")
 */

export interface ApiResponse<T> {
  data: T;
  source: SourceMeta;
  freshness?: Freshness;
  disclaimer: string;
}

export interface SourceMeta {
  provider: string;
  license: string;
  url: string;
  retrievedAt: string;
}

export interface Freshness {
  lastSyncedAt?: string;
  stale: boolean;
  staleThresholdHours: number;
}

export interface LawSummary {
  lsId: string;
  nameKr: string;
  shortName?: string;
  lsiSeq: string;
  effectiveDate: string;
  promulgationDate: string;
  revisionType?: string;
}

export interface ArticleDto {
  jo: number;
  joBranch?: number;
  hang?: number;
  ho?: number;
  mok?: string;
  title?: string;
  body: string;
  effectiveDate?: string;
}

export interface ArticleSearchHit {
  law: LawSummary;
  article: ArticleDto;
  distance: number;
}

export interface ArticleSearchRequest {
  query: string;
  limit?: number;
}

export interface ArticleSearchResponse {
  query: string;
  hits: ArticleSearchHit[];
}

export interface ArticleListResponse {
  law: LawSummary;
  articles: ArticleDto[];
}
