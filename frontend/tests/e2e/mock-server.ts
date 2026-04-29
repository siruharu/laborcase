import http from "node:http";

import {
  articleListResponse,
  listLawsResponse,
  searchResponse,
} from "./fixtures/api";

/**
 * Visual smoke 용 mock backend.
 * Playwright 의 page.route 는 RSC 의 server-side fetch 를 가로채지 못하므로,
 * 별도 HTTP 서버를 띄워 NEXT_PUBLIC_API_BASE_URL 을 그쪽으로 가리킨다.
 *
 * 시나리오마다 stale / emptySearch flag 가 달라지므로 /__test/state 엔드포인트
 * 로 사전 toggle. 각 테스트 beforeEach 에서 상태 초기화.
 */

const PORT = Number(process.env.MOCK_PORT ?? "18080");

const STATE = {
  stale: false,
  emptySearch: false,
};

const server = http.createServer((req, res) => {
  const url = new URL(req.url ?? "/", `http://localhost:${PORT}`);

  // Test toggle / reset.
  if (url.pathname === "/__test/state") {
    STATE.stale = url.searchParams.get("stale") === "1";
    STATE.emptySearch = url.searchParams.get("emptySearch") === "1";
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify(STATE));
    return;
  }
  if (url.pathname === "/__test/reset") {
    STATE.stale = false;
    STATE.emptySearch = false;
    res.writeHead(200).end("ok");
    return;
  }

  // CORS preflight (정확한 backend 동작 모방, 실제 frontend 가 same-origin
  // fetch 를 RSC 에서 보내지만 보수적으로 허용).
  if (req.method === "OPTIONS") {
    res.writeHead(204, {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET,POST,OPTIONS",
      "Access-Control-Allow-Headers": "content-type,accept",
      "Access-Control-Max-Age": "3600",
    });
    res.end();
    return;
  }

  // GET /api/v1/laws
  if (url.pathname === "/api/v1/laws" && req.method === "GET") {
    sendJson(res, listLawsResponse({ stale: STATE.stale }));
    return;
  }

  // POST /api/v1/articles/search
  if (url.pathname === "/api/v1/articles/search" && req.method === "POST") {
    let body = "";
    req.on("data", (c) => (body += c));
    req.on("end", () => {
      const parsed = body ? (JSON.parse(body) as { query?: string }) : {};
      sendJson(
        res,
        searchResponse(parsed.query ?? "", {
          empty: STATE.emptySearch,
          stale: STATE.stale,
        }),
      );
    });
    return;
  }

  // GET /api/v1/laws/{key}/articles?jo=...
  const articleMatch = url.pathname.match(/^\/api\/v1\/laws\/[^/]+\/articles\/?$/);
  if (articleMatch && req.method === "GET") {
    const jo = Number.parseInt(url.searchParams.get("jo") ?? "0", 10);
    sendJson(res, articleListResponse({ jo, stale: STATE.stale }));
    return;
  }

  res.writeHead(404, { "Content-Type": "application/json" });
  res.end(JSON.stringify({ error: "not found", path: url.pathname }));
});

function sendJson(res: http.ServerResponse, data: unknown) {
  res.writeHead(200, { "Content-Type": "application/json" });
  res.end(JSON.stringify(data));
}

server.listen(PORT, () => {
  console.log(`mock backend listening on http://localhost:${PORT}`);
});
