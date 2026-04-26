/**
 * 검색 결과 영역 상단에 한 줄로 들어가는 안내.
 * 분석 §P3-c 의 layer 2.
 *
 * 결과 페이지에서는 source(ApiResponse.source) 의 url 을 그대로 받아 "원문 보기"
 * 링크를 만든다.
 */
interface Props {
  sourceUrl: string;
}

export function DisclaimerInline({ sourceUrl }: Props) {
  return (
    <p className="text-xs leading-relaxed text-muted-foreground">
      아래 결과는 법제처 국가법령정보(공공누리 제1유형)에 기반한 참고
      자료입니다. 자세한 내용은{" "}
      <a
        href={sourceUrl}
        target="_blank"
        rel="noopener noreferrer"
        className="underline underline-offset-2 hover:text-foreground"
      >
        원문 보기 ↗
      </a>
      .
    </p>
  );
}
