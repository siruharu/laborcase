import { Skeleton } from "@/components/ui/skeleton";

export default function Loading() {
  return (
    <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-5 px-6 py-10 sm:py-14">
      <Skeleton className="h-10 w-full" />
      <div className="space-y-2 border-b border-border pb-4">
        <Skeleton className="h-3 w-24" />
        <Skeleton className="h-7 w-1/2" />
      </div>
      {Array.from({ length: 5 }).map((_, i) => (
        <div key={i} className="space-y-2">
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-11/12" />
          <Skeleton className="h-4 w-3/4" />
        </div>
      ))}
    </main>
  );
}
