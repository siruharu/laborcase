import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Cloud Run 배포용. `next build` 가 .next/standalone 디렉토리를 만들어
  // node_modules 없이 `node server.js` 만으로 동작하는 self-contained
  // 서버를 출력한다. Dockerfile 의 runner stage 가 이 결과를 그대로 복사.
  output: "standalone",
};

export default nextConfig;
