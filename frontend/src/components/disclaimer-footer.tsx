/**
 * 모든 페이지 footer 에 고정되는 디스클레이머.
 *
 * - 1행: 데이터 출처 (공공누리 제1유형 출처표시 의무 — docs/legal/source-attribution.md).
 * - 2행: CLAUDE.md §UI 필수 요소 의 풀텍스트 디스클레이머.
 * - 3행: 운영자 + 라이선스 표기.
 *
 * 4-layer disclaimer (분석 §P3-c) 의 최외곽 레이어.
 * 다른 layer:
 *   - {@link DisclaimerInline} 결과 상단 한 줄 안내
 *   - {@link DisclaimerDetailBox} 조문 상세 박스
 *   - {@link StaleBanner} freshness.stale=true 시 amber 배너
 */
export function DisclaimerFooter() {
  return (
    <footer className="mt-auto border-t border-border bg-muted/30">
      <div className="mx-auto max-w-3xl px-6 py-6 text-xs leading-relaxed text-muted-foreground">
        <p>
          본 서비스는{" "}
          <a
            href="https://www.law.go.kr"
            target="_blank"
            rel="noopener noreferrer"
            className="underline underline-offset-2 hover:text-foreground"
          >
            법제처 국가법령정보센터
          </a>
          의 법령 데이터를{" "}
          <a
            href="https://www.kogl.or.kr/info/license.do#01-tab"
            target="_blank"
            rel="noopener noreferrer"
            className="underline underline-offset-2 hover:text-foreground"
          >
            공공누리 제1유형(출처표시)
          </a>
          에 따라 이용합니다.
        </p>
        <p className="mt-2">
          본 정보는 공개된 판례 및 법령에 기반한 참고 자료이며, 법률 자문이
          아닙니다. 구체적 사건은 반드시 변호사·노무사와 상담하세요.
        </p>
        <p className="mt-2">© 2026 laborcase · Apache-2.0</p>
      </div>
    </footer>
  );
}
