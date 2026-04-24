# ai/prompts.example

외부 기여자·신규 개발자·fork PR 빌드용 **공개 샘플 프롬프트**.
실제 운영 프롬프트는 [`laborcase-internal`](https://github.com/siruharu/laborcase-internal) private 레포의 submodule (`ai/prompts/`) 에 있으며, 접근 권한이 있는 경우에만 체크아웃된다.

## 동작 방식

- `git clone --recurse-submodules` + submodule 접근 권한 → `ai/prompts/` 채워짐, 실제 프롬프트로 빌드.
- 권한 없음 or `--no-recurse-submodules` → `ai/prompts/` 비어있음, 앱은 이 `ai/prompts.example/` 을 대신 로드해 경고와 함께 기동.

## 담는 것

- 법적 표현 제약(단정 표현 금지, 원문 링크 필수) 을 **구조적으로만** 보여주는 최소 샘플.
- 빌드 파이프라인 회귀 테스트용 placeholder.

## 담지 않는 것

- 실제 운영에 쓰이는 문구·퓨샷·체크리스트 원문.
- 경쟁사 복제 시 가치 희석되는 세부 로직.
