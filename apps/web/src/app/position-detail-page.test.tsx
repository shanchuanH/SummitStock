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
    targetWeightMin: "0.04",
    targetWeightMax: "0.08",
    confidence: "MEDIUM",
    reasons: ["Company quality remains healthy."],
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
    strategyVersion: "3.0.0-draft",
    configHash: "hash",
  },
  layers: {
    systemRecommendation: {
      action: "HOLD_DO_NOT_ADD",
      priority: "DO_NOT",
      confidence: "MEDIUM",
      exactQuantityAllowed: false,
    },
    portfolioRole: {
      classification: "QUALITY_STOCK",
      currentWeight: "0.156",
      targetWeightMin: "0.04",
      targetWeightMax: "0.08",
      normalMaxWeight: "0.12",
      hardMaxWeight: "0.15",
      atHardMax: true,
      capacityExplanation: "At hard limit.",
    },
    fundamentals: {
      financialHealth: "HEALTHY",
      quality: "HEALTHY",
      available: true,
    },
    valuation: {
      state: "ATTRACTIVE",
      confidence: "MEDIUM",
      observationCount: 12,
      independentConfirmation: true,
      attractive: true,
      attractiveButCannotAdd: true,
    },
    priceRiskEarnings: {
      priceState: "UPTREND",
      formalStop: "170",
      liveStop: "175",
      earningsRisk: "ELEVATED",
    },
    rationaleAndEvidence: {
      reasons: ["Company quality remains healthy."],
      risks: ["Technology cluster concentration."],
      changeConditions: ["Material earnings deterioration"],
      evidenceDrawer: {
        ruleIds: ["RISK_CAP"],
        evidenceRefs: ["financial-health:GOOGL"],
        strategyVersion: "3.0.0-draft",
        configHash: "hash",
        dataQuality: "HEALTHY",
        dataAsOf: "2026-08-05T20:00:00Z",
      },
    },
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
  entryMarkers: [],
  stopSeries: [],
  earningsMarkers: [],
  tradeMarkers: [],
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
function url(input: string | URL | Request) {
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

describe("PositionDetailPage", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });
  it("renders the six-layer analyst report and real chart without false precision", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((input: string | URL | Request) => {
        const path = url(input);
        if (path.endsWith("/analyst-report")) return response(report);
        if (path.includes("/chart")) return response(chart);
        if (path.endsWith("/intelligence")) return response({});
        throw new Error(path);
      }),
    );
    renderPage();
    expect(
      await screen.findByRole("heading", { name: "GOOGL" }),
    ).toBeInTheDocument();
    expect(
      screen.getAllByText(/当前无需交易或证据不足，未提供精确数量/),
    ).toHaveLength(2);
    expect(
      screen.getByText(/估值可能有吸引力，但组合仓位已触及上限/),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("真实持仓图表")).toHaveTextContent(
      "1 根真实日线",
    );
    expect(document.querySelectorAll(".module-number")).toHaveLength(6);
    expect(screen.getByText(/规则：/).parentElement).toHaveTextContent(
      "RISK_CAP",
    );
  });
  it("uses explicit unavailable state when report evidence is missing", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((input: string | URL | Request) =>
        url(input).endsWith("/analyst-report")
          ? response({ detail: "not ready" }, 409)
          : response({}),
      ),
    );
    renderPage();
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "持仓分析尚不可用",
    );
  });
});
