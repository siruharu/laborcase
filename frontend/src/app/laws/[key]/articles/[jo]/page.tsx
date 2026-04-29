import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";

import { ArticleTree } from "@/components/article-tree";
import { DisclaimerDetailBox } from "@/components/disclaimer-detail-box";
import { StaleBanner } from "@/components/stale-banner";
import { ApiError, getLawArticles } from "@/lib/api";

interface PageProps {
  params: Promise<{ key: string; jo: string }>;
}

export async function generateMetadata({
  params,
}: PageProps): Promise<Metadata> {
  const { key, jo } = await params;
  const decodedKey = decodeURIComponent(key);
  return {
    title: `${decodedKey} 제${jo}조 — laborcase`,
  };
}

export default async function ArticleDetailPage({ params }: PageProps) {
  const { key: rawKey, jo: rawJo } = await params;
  const key = decodeURIComponent(rawKey);
  const jo = Number.parseInt(rawJo, 10);
  if (Number.isNaN(jo) || jo < 1) notFound();

  let response;
  try {
    response = await getLawArticles(key, jo);
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) notFound();
    throw e;
  }

  if (response.data.articles.length === 0) notFound();

  return (
    <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-5 px-6 py-10 sm:py-14">
      <StaleBanner freshness={response.freshness} />

      <nav className="text-xs text-muted-foreground">
        <Link href="/" className="underline-offset-2 hover:underline">
          ← 처음으로
        </Link>
      </nav>

      <DisclaimerDetailBox
        effectiveDate={response.data.law.effectiveDate}
        sourceUrl={response.source.url}
      />

      <ArticleTree
        law={response.data.law}
        articles={response.data.articles}
      />
    </main>
  );
}
