/**
 * Playwright fetch mock 용 백엔드 응답 fixture.
 *
 * CLAUDE.md §사용자가 따를 원칙 — "프로덕션 DB 에는 Claude Code 접근 권한
 * 부여 금지". 자동 smoke 는 backend 응답을 mock 으로 픽스하고, 실제 backend +
 * Cloud SQL 통합 검증은 사용자 환경에서 별도로 수행한다.
 */

import type {
  ApiResponse,
  ArticleListResponse,
  ArticleSearchResponse,
  LawSummary,
} from "../../../src/types/api";

const KOGL_SOURCE = {
  provider: "법제처 국가법령정보센터",
  license: "KOGL-1",
  url: "https://www.law.go.kr",
  retrievedAt: "2026-04-27T00:00:00Z",
};

const FRESH = {
  lastSyncedAt: "2026-04-26T18:00:00Z",
  stale: false,
  staleThresholdHours: 48,
};

const STALE = {
  lastSyncedAt: "2026-04-22T18:00:00Z",
  stale: true,
  staleThresholdHours: 48,
};

const DISCLAIMER =
  "본 정보는 공개된 판례 및 법령에 기반한 참고 자료이며, 법률 자문이 아닙니다. " +
  "구체적 사건은 반드시 변호사·노무사와 상담하세요.";

export const LAWS: LawSummary[] = [
  {
    lsId: "001872",
    nameKr: "근로기준법",
    shortName: "근기법",
    lsiSeq: "265959",
    effectiveDate: "2025-10-23",
    promulgationDate: "2024-10-22",
    revisionType: "일부개정",
  },
  {
    lsId: "002138",
    nameKr: "최저임금법",
    shortName: "최임법",
    lsiSeq: "264512",
    effectiveDate: "2024-01-01",
    promulgationDate: "2023-08-15",
    revisionType: "일부개정",
  },
  {
    lsId: "003001",
    nameKr: "근로자퇴직급여 보장법",
    shortName: "퇴직급여법",
    lsiSeq: "260111",
    effectiveDate: "2024-07-01",
    promulgationDate: "2023-12-08",
    revisionType: "일부개정",
  },
  {
    lsId: "004099",
    nameKr: "남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률",
    shortName: "남녀고용평등법",
    lsiSeq: "258900",
    effectiveDate: "2025-01-01",
    promulgationDate: "2024-05-01",
    revisionType: "일부개정",
  },
  {
    lsId: "005120",
    nameKr: "기간제 및 단시간근로자 보호 등에 관한 법률",
    shortName: "기간제법",
    lsiSeq: "247733",
    effectiveDate: "2023-04-11",
    promulgationDate: "2022-12-27",
    revisionType: "일부개정",
  },
  {
    lsId: "006077",
    nameKr: "파견근로자 보호 등에 관한 법률",
    shortName: "파견법",
    lsiSeq: "240015",
    effectiveDate: "2022-06-10",
    promulgationDate: "2021-09-29",
    revisionType: "일부개정",
  },
];

export function listLawsResponse(opts: { stale?: boolean } = {}): ApiResponse<
  LawSummary[]
> {
  return {
    data: LAWS,
    source: KOGL_SOURCE,
    freshness: opts.stale ? STALE : FRESH,
    disclaimer: DISCLAIMER,
  };
}

export function searchResponse(
  query: string,
  opts: { empty?: boolean; stale?: boolean } = {},
): ApiResponse<ArticleSearchResponse> {
  const hits = opts.empty
    ? []
    : [
        {
          law: LAWS[0]!,
          article: {
            jo: 28,
            hang: 1,
            body:
              "① 사용자가 근로자에게 부당해고등을 하면 근로자는 노동위원회에 구제를 신청할 수 있다.",
          },
          distance: 0.5407,
        },
        {
          law: LAWS[0]!,
          article: {
            jo: 23,
            hang: 1,
            body:
              "① 사용자는 근로자에게 정당한 이유 없이 해고, 휴직, 정직, 전직, 감봉, 그 밖의 징벌을 하지 못한다.",
          },
          distance: 0.6095,
        },
        {
          law: LAWS[0]!,
          article: {
            jo: 27,
            hang: 1,
            body: "① 사용자는 근로자를 해고하려면 해고사유와 해고시기를 서면으로 통지하여야 한다.",
          },
          distance: 0.6712,
        },
        {
          law: LAWS[0]!,
          article: {
            jo: 26,
            body: "사용자는 근로자를 해고(경영상 이유에 의한 해고를 포함한다)하려면 적어도 30일 전에 예고를 하여야 한다.",
          },
          distance: 0.7012,
        },
        {
          law: LAWS[0]!,
          article: {
            jo: 30,
            hang: 1,
            body: "① 노동위원회는 제28조에 따른 구제신청에 따라 조사를 마치면…",
          },
          distance: 0.7501,
        },
      ];

  return {
    data: { query, hits },
    source: KOGL_SOURCE,
    freshness: opts.stale ? STALE : FRESH,
    disclaimer: DISCLAIMER,
  };
}

export function articleListResponse(opts: {
  jo: number;
  stale?: boolean;
}): ApiResponse<ArticleListResponse> {
  const articles = opts.jo === 23
    ? [
        {
          jo: 23,
          title: "해고 등의 제한",
          body: "제23조(해고 등의 제한)",
          effectiveDate: "2025-10-23",
        },
        {
          jo: 23,
          hang: 1,
          body: "① 사용자는 근로자에게 정당한 이유 없이 해고, 휴직, 정직, 전직, 감봉, 그 밖의 징벌(이하 \"부당해고등\"이라 한다)을 하지 못한다.",
        },
        {
          jo: 23,
          hang: 2,
          body: "② 사용자는 근로자가 업무상 부상 또는 질병의 요양을 위하여 휴업한 기간과 그 후 30일 동안 또는 산전·산후의 여성이 이 법에 따라 휴업한 기간과 그 후 30일 동안은 해고하지 못한다. 다만, 사용자가 제84조에 따라 일시보상을 하였을 경우 또는 사업을 계속할 수 없게 된 경우에는 그러하지 아니하다.",
        },
        {
          jo: 23,
          hang: 3,
          ho: 1,
          body: "1. 다음 각 목의 어느 하나에 해당하는 사유",
        },
        {
          jo: 23,
          hang: 3,
          ho: 1,
          mok: "가",
          body: "가. 천재지변 또는 그 밖의 부득이한 사유로 사업을 계속할 수 없게 된 경우",
        },
        {
          jo: 23,
          hang: 3,
          ho: 1,
          mok: "나",
          body: "나. 사용자가 제30조에 따른 구제명령을 이행하지 아니한 경우",
        },
      ]
    : [];

  return {
    data: { law: LAWS[0]!, articles },
    source: {
      ...KOGL_SOURCE,
      url: "https://www.law.go.kr/lsInfoP.do?lsiSeq=265959",
    },
    freshness: opts.stale ? STALE : FRESH,
    disclaimer: DISCLAIMER,
  };
}
