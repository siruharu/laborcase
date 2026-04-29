import { Card } from "@/components/ui/card";

/**
 * 검색 결과가 0건일 때 표시되는 검색 팁 3장.
 * 분석 §P2-d. PoC 의 약점 케이스 (Q5 "부서를 옮기래요") 처럼 비유적
 * 표현이 매칭 약한 점을 사용자에게 미리 안내한다.
 */
const TIPS: { title: string; body: string }[] = [
  {
    title: "구체적 상황을 한 줄로",
    body: "예: \"월급이 한 달째 안 들어와요\", \"출산휴가 중에 잘렸어요\".",
  },
  {
    title: "법률 용어 대신 일상 표현",
    body: "어려운 용어를 모르더라도 본인이 겪은 상황 그대로 쓰면 됩니다.",
  },
  {
    title: "원문은 법제처 국가법령정보센터",
    body: "결과 카드의 \"원문 보기\" 링크는 모두 법제처 국가법령정보센터로 연결됩니다.",
  },
];

export function SearchTips() {
  return (
    <div className="space-y-3">
      <p className="text-sm font-medium text-foreground">검색 팁</p>
      <ul className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        {TIPS.map((tip) => (
          <li key={tip.title}>
            <Card className="h-full">
              <div className="flex flex-col gap-1.5 px-4 py-3">
                <p className="text-sm font-semibold text-foreground">
                  {tip.title}
                </p>
                <p className="text-xs leading-relaxed text-muted-foreground">
                  {tip.body}
                </p>
              </div>
            </Card>
          </li>
        ))}
      </ul>
    </div>
  );
}
