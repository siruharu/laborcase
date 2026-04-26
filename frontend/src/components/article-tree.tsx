import { formatArticleNumber } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { ArticleDto, LawSummary } from "@/types/api";

/**
 * 조문 상세 트리. backend 가 이미 hang/ho/mok 별 row 단위로 내려주므로
 * 깊이만 계산해 들여쓰기 한다. body 에 ①, "1.", "가." 마커가 포함되어
 * 있어 추가 prefix 는 붙이지 않는다.
 *
 * 깊이:
 *   0 — 조 헤더 (hang null & ho null & mok null)
 *   1 — 항    (hang only)
 *   2 — 호    (hang + ho)
 *   3 — 목    (hang + ho + mok)
 */
interface Props {
  law: LawSummary;
  articles: ArticleDto[];
}

export function ArticleTree({ law, articles }: Props) {
  const header = articles.find(
    (a) => a.hang == null && a.ho == null && a.mok == null,
  );
  const body = articles.filter((a) => a !== header);

  // 첫 번째 articles[0] 이 항상 존재한다고 가정 (page 가 빈 응답에 notFound).
  const first = articles[0]!;
  const headline =
    header?.body ?? formatArticleNumber(first.jo, first.joBranch ?? undefined);

  return (
    <article className="space-y-5">
      <header className="space-y-1.5 border-b border-border pb-4">
        <p className="text-xs font-medium text-muted-foreground">
          {law.shortName ? `${law.nameKr} (${law.shortName})` : law.nameKr}
        </p>
        <h1 className="text-2xl font-bold tracking-tight text-foreground">
          {headline}
        </h1>
        {header?.title && !headline.includes(header.title) && (
          <p className="text-base text-muted-foreground">{header.title}</p>
        )}
      </header>

      <div className="space-y-3">
        {body.map((a, idx) => (
          <p
            key={`${a.hang ?? 0}-${a.ho ?? 0}-${a.mok ?? ""}-${idx}`}
            className={cn(
              "leading-relaxed text-foreground/90",
              depthClass(getDepth(a)),
            )}
          >
            {a.body}
          </p>
        ))}
      </div>
    </article>
  );
}

function getDepth(a: ArticleDto): 1 | 2 | 3 {
  if (a.mok != null) return 3;
  if (a.ho != null) return 2;
  return 1;
}

function depthClass(depth: 1 | 2 | 3): string {
  switch (depth) {
    case 1:
      return "pl-0";
    case 2:
      return "pl-5 sm:pl-7";
    case 3:
      return "pl-10 sm:pl-14";
  }
}
