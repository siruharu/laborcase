import { formatKoDate } from "@/lib/format";

/**
 * 조문 상세 페이지 상단에 박스 형태로 표시되는 안내.
 * 분석 §P3-c 의 layer 3.
 *
 * effectiveDate 는 `LawSummary.effectiveDate` (LocalDate ISO),
 * sourceUrl 은 ApiResponse.source.url (해당 lsiSeq 의 법제처 lsInfoP URL).
 */
interface Props {
  effectiveDate: string;
  sourceUrl: string;
}

export function DisclaimerDetailBox({ effectiveDate, sourceUrl }: Props) {
  return (
    <div className="rounded-md border border-border bg-muted/40 px-4 py-3 text-sm text-muted-foreground">
      이 조문은 <strong className="font-semibold text-foreground">{formatKoDate(effectiveDate)}</strong>{" "}
      시행 버전입니다.{" "}
      <a
        href={sourceUrl}
        target="_blank"
        rel="noopener noreferrer"
        className="underline underline-offset-2 hover:text-foreground"
      >
        원문 보기 ↗
      </a>
    </div>
  );
}
