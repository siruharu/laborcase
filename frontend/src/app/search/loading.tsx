import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";

/**
 * /search 검색 결과 RSC 가 fetch 하는 동안 보이는 스켈레톤.
 * SearchBox 영역만 인터랙티브하게 마운트해 두지 않고 page.tsx 가 동일
 * SearchBox 를 렌더하므로 여기는 결과 카드 5장만 placeholder.
 */
export default function Loading() {
  return (
    <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-6 px-6 py-10 sm:py-14">
      <Skeleton className="h-12 w-full" />
      <Skeleton className="h-4 w-2/3" />
      <ul className="space-y-3">
        {Array.from({ length: 5 }).map((_, i) => (
          <li key={i}>
            <Card className="h-full">
              <div className="flex flex-col gap-2 px-4 py-3">
                <div className="flex items-center gap-2">
                  <Skeleton className="h-5 w-12" />
                  <Skeleton className="h-5 w-20" />
                  <Skeleton className="ml-auto h-5 w-16" />
                </div>
                <Skeleton className="h-4 w-full" />
                <Skeleton className="h-4 w-11/12" />
                <Skeleton className="h-4 w-3/4" />
              </div>
            </Card>
          </li>
        ))}
      </ul>
    </main>
  );
}
