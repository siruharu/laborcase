import Link from "next/link";

import { RelevanceBadge } from "@/components/relevance-badge";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { formatArticleNumber } from "@/lib/format";
import type { ArticleSearchHit } from "@/types/api";

/**
 * 검색 결과 1건을 표시하는 카드.
 * 카드 전체가 조문 상세 페이지(/laws/{key}/articles/{jo})로 가는 Link.
 */
interface Props {
  hit: ArticleSearchHit;
}

export function ArticleCard({ hit }: Props) {
  const lawDisplay = hit.law.shortName ?? hit.law.nameKr;
  const href = `/laws/${encodeURIComponent(lawDisplay)}/articles/${hit.article.jo}`;
  const articleNo = formatArticleNumber(
    hit.article.jo,
    hit.article.joBranch,
    hit.article.hang,
    hit.article.ho,
  );

  return (
    <li>
      <Link
        href={href}
        className="block rounded-xl focus:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        <Card className="h-full transition-colors hover:bg-muted/40">
          <div className="flex flex-col gap-2 px-4 py-3">
            <div className="flex flex-wrap items-center gap-2">
              <Badge variant="outline" className="text-[11px]">
                {lawDisplay}
              </Badge>
              <span className="text-sm font-semibold text-foreground">
                {articleNo}
              </span>
              {hit.article.title && (
                <span className="text-sm text-muted-foreground">
                  ({hit.article.title})
                </span>
              )}
              <span className="ml-auto">
                <RelevanceBadge distance={hit.distance} />
              </span>
            </div>
            <p className="line-clamp-3 text-sm leading-relaxed text-foreground/90">
              {hit.article.body}
            </p>
            <div className="text-xs text-muted-foreground">
              조문 상세 보기 →
            </div>
          </div>
        </Card>
      </Link>
    </li>
  );
}
