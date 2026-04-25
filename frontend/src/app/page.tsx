/**
 * Pretendard GOV 폰트 검증용 임시 페이지.
 * FT-Task 6 (랜딩 페이지) 에서 Hero + 법령 카드 그리드로 덮어쓴다.
 */
export default function Home() {
  return (
    <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-8 px-6 py-16">
      <header className="space-y-2">
        <h1 className="text-3xl font-bold tracking-tight text-foreground">
          laborcase 프론트 폰트 미리보기
        </h1>
        <p className="text-base text-muted-foreground">
          Pretendard GOV (subset, 4 weights) 적용 확인용. FT-Task 6 에서 본
          랜딩으로 교체된다.
        </p>
      </header>

      <section className="space-y-3">
        <h2 className="text-xl font-semibold">웨이트 샘플</h2>
        <p className="font-normal">
          400 Regular — 사용자는 정당한 이유 없이 근로자를 해고하지 못한다.
        </p>
        <p className="font-medium">
          500 Medium — 사용자는 정당한 이유 없이 근로자를 해고하지 못한다.
        </p>
        <p className="font-semibold">
          600 SemiBold — 사용자는 정당한 이유 없이 근로자를 해고하지 못한다.
        </p>
        <p className="font-bold">
          700 Bold — 사용자는 정당한 이유 없이 근로자를 해고하지 못한다.
        </p>
      </section>

      <section className="space-y-2 rounded-lg border border-border bg-card p-6">
        <h2 className="text-xl font-semibold">
          근로기준법 제23조 (해고 등의 제한)
        </h2>
        <p>
          ① 사용자는 근로자에게 정당한 이유 없이 해고, 휴직, 정직, 전직, 감봉,
          그 밖의 징벌(이하 &ldquo;부당해고등&rdquo;이라 한다)을 하지 못한다.
        </p>
        <p>
          ② 사용자는 근로자가 업무상 부상 또는 질병의 요양을 위하여 휴업한
          기간과 그 후 30일 동안 또는 산전(産前)·산후(産後)의 여성이 이 법에
          따라 휴업한 기간과 그 후 30일 동안은 해고하지 못한다.
        </p>
      </section>

      <section className="rounded-lg border border-[var(--color-stale-border)] bg-[var(--color-stale-bg)] p-4 text-sm text-[var(--color-stale-foreground)]">
        ⚠ stale 배너 색상 토큰 미리보기 — freshness.stale=true 시 결과/상세
        페이지 상단에 동일 톤으로 표시된다.
      </section>

      <section className="inline-block self-start rounded-md bg-[var(--color-brand)] px-4 py-2 text-sm font-medium text-[var(--color-brand-foreground)]">
        brand 토큰 (teal-600) 미리보기 — &ldquo;관련도 높음&rdquo; 라벨에 사용
        예정.
      </section>
    </main>
  );
}
