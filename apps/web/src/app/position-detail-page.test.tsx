import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { PositionDetailPage } from "./position-detail-page";

vi.mock("./position-chart", () => ({
  PositionChart: ({ data }: { data: { bars: unknown[] } }) => (
    <div aria-label="真实持仓图表">{data.bars.length} 根真实日线</div>
  ),
}));
const report = {
  position: {
    id: "p1",
    symbol: "GOOGL",
    classification: "QUALITY_STOCK",
    classificationSource: "USER_CONFIRMED",
  },
  readiness: "READY",
  recommendation: {
    id: "r1",
    action: "HOLD_DO_NOT_ADD",
    priority: "DO_NOT",
    quantityMin: null,
    quantityMax: null,
    currentWeight: "0.156",
    targetWeightMin: "0.08",
    targetWeightMax: "0.12",
    confidence: "MEDIUM",
    reasons: [
      "Company quality remains healthy.",
      "Valuation does not offer enough discount.",
    ],
    risks: ["Technology cluster concentration."],
    changeConditions: ["Material earnings deterioration"],
    winningRule: "RISK_CAP",
    resolutionReason: "Current weight is above target.",
    validUntil: "2026-09-01T00:00:00Z",
  },
  evidence: {
    analysisStatus: "READY",
    exactQuantityAllowed: false,
    ruleIds: ["RISK_CAP"],
    strategyVersion: "v1",
    configHash: "hash",
  },
  dataAsOf: "2026-08-05T20:00:00Z",
};
const chart = {
  bars: [
    {
      marketDate: "2026-08-05",
      open: "100",
      high: "105",
      low: "99",
      close: "104",
      quality: "HEALTHY",
    },
  ],
  entryMarkers: [
    { marketDate: "2026-08-05", price: "101", markerType: "AVERAGE_COST" },
  ],
  stopSeries: [
    {
      marketDate: "2026-08-05",
      formalStop: "94",
      liveStop: "95",
      softAlert: "97",
    },
  ],
  earningsMarkers: [
    { marketDate: "2026-08-20", markerType: "EARNINGS", label: "Earnings" },
  ],
  tradeMarkers: [],
  dataAsOf: "2026-08-05T20:00:00Z",
  quality: "HEALTHY",
};
function response(value: unknown, status = 200) {
  return Promise.resolve(
    new Response(JSON.stringify(value), {
      status,
      headers: { "Content-Type": "application/json" },
    }),
  );
}
function requestUrl(input: string | URL | Request) {
  return input instanceof Request ? input.url : input.toString();
}
function renderPage() {
  return render(
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retry: false } } })
      }
    >
      <MemoryRouter initialEntries={["/positions/p1"]}>
        <Routes>
          <Route
            path="/positions/:positionId"
            element={<PositionDetailPage />}
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("PositionDetailPageTest", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });
  it("renders the twelve modules and real chart contract without false precision", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((input: string | URL | Request) => {
        const url = requestUrl(input);
        if (url.endsWith("/report")) return response(report);
        if (url.includes("/chart")) return response(chart);
        if (url.endsWith("/journal"))
          return response([
            {
              id: "j1",
              entryType: "IMPORT",
              taxStatus: "UNKNOWN",
              realizedR: null,
              mfeR: null,
              maeR: null,
              exitReason: null,
            },
          ]);
        throw new Error(url);
      }),
    );
    renderPage();
    expect(
      await screen.findByRole("heading", { name: "GOOGL" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/当前无需交易或证据不足，未提供精确数量/),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("真实持仓图表")).toHaveTextContent(
      "1 根真实日线",
    );
    expect(
      screen.getAllByText(
        /最终结论|组合中的角色|公司质量|估值|价格趋势|风险和 Stops|Thesis|财报 \/ 事件|Cluster overlap|Tax lots|数据来源和时效|决策历史/,
      ),
    ).toHaveLength(12);
    expect(screen.getByText(/规则：RISK_CAP/)).toBeInTheDocument();
  });
  it("uses an explicit unavailable state when report evidence is missing", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((input: string | URL | Request) =>
        requestUrl(input).endsWith("/report")
          ? response({ detail: "not ready" }, 409)
          : response({
              bars: [],
              entryMarkers: [],
              stopSeries: [],
              earningsMarkers: [],
              tradeMarkers: [],
              quality: "MISSING",
            }),
      ),
    );
    renderPage();
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "持仓分析尚不可用",
    );
    expect(screen.queryByText(/HOLD_DO_NOT_ADD/)).not.toBeInTheDocument();
  });
});
