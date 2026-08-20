import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
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
    narrative: {
      oneSentence: "长期逻辑仍成立，但仓位已经太大。",
    },
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
    market: {
      price: "196.14",
      dayChangePct: "0.012",
      oneMonthReturn: "0.053",
      threeMonthReturn: "0.101",
      averageCost: "172.02",
      unrealizedPnlDollar: "1977",
      unrealizedPnlPct: "0.14",
    },
    fundamentals: {
      financialHealth: "HEALTHY",
      revenueTtm: "350000000000",
      revenueYoy: "0.14",
      epsTtm: "8.23",
      operatingMargin: "0.32",
      fcfTtm: "72000000000",
      fcfMargin: "0.205",
      netCash: "98000000000",
      shareDilutionYoy: "0.006",
      quality: "HEALTHY",
      available: true,
    },
    valuation: {
      state: "ATTRACTIVE",
      trailingPeTtm: "24.8",
      forwardPeFy1: "21.4",
      evSalesTtm: "6.2",
      fcfYieldTtm: "0.036",
      historyPercentile5y: "0.42",
      confidence: "MEDIUM",
      observationCount: 12,
      independentConfirmation: true,
      attractive: true,
      attractiveButCannotAdd: true,
    },
    estimates: {
      fy1Eps: "8.42",
      eps30dAgo: "8.25",
      eps90dAgo: "8.11",
      epsRevision30d: "0.018",
      epsRevision90d: "0",
      analystCount: 39,
      epsHigh: "9.10",
      epsLow: "7.65",
      dispersion: "0.17",
      dispersionHigh: false,
      state: "POSITIVE",
      quality: "HEALTHY",
    },
    technical: {
      sma50: "188",
      sma200: "170",
      distanceFromSma50: "0.043",
      distanceFromSma200: "0.154",
      rsi14: "56",
      atrPercent: "0.021",
      relativeStrengthQqq3m: "0.031",
    },
    earnings: {
      nextEarningsAt: "2026-10-20T20:00:00Z",
      eventRisk: "ELEVATED",
      historicalP75AbsMove: "0.07",
      reaction1d: [],
      reaction3d: [],
      reaction5d: [],
    },
    risk: {
      currentWeight: "0.156",
      normalMaxWeight: "0.12",
      hardMaxWeight: "0.15",
      plannedStop: "170",
      stopDistancePct: "0.133",
      positionPlannedRiskDollar: "520",
      positionPlannedRiskPct: "0.0034",
      clusterRisk: "0.011",
      totalPortfolioPlannedRisk: "0.019",
      sizingLimitingConstraint: "TOTAL_RISK_CAP",
      quality: "HEALTHY",
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
  it("puts the answer first and defaults only layers one and two open", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((input: string | URL | Request) => {
        const path = url(input);
        if (path.endsWith("/analyst-report")) return response(report);
        if (path.includes("/chart")) return response(chart);
        throw new Error(path);
      }),
    );
    renderPage();
    expect(
      await screen.findByRole("heading", { name: "GOOGL" }),
    ).toBeInTheDocument();
    expect(
      screen.getAllByText(/当前无需交易或证据不足，未提供精确数量/),
    ).toHaveLength(1);
    expect(
      screen.getByText("长期逻辑仍成立，但仓位已经太大。"),
    ).toBeInTheDocument();
    const layers = Array.from(document.querySelectorAll(".analyst-layer"));
    expect(layers).toHaveLength(6);
    expect(layers.map((layer) => (layer as HTMLDetailsElement).open)).toEqual([
      true,
      true,
      false,
      false,
      false,
      false,
    ]);
    expect(screen.getByText("Revenue TTM").parentElement).toHaveTextContent(
      "3500",
    );
    expect(screen.getAllByText("24.8×")).toHaveLength(2);
    expect(screen.getByText("US$8.25")).toBeInTheDocument();
    expect(screen.getByText("US$8.11")).toBeInTheDocument();
    expect(screen.getByText("17.0%")).toBeInTheDocument();
    expect(screen.getByText("暂不可用")).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(
      "当前只积累了 12 个 point-in-time observations",
    );
    expect(screen.getByLabelText("真实持仓图表")).toHaveTextContent(
      "1 根真实日线",
    );
    expect(document.querySelectorAll(".module-number")).toHaveLength(6);
    expect(screen.getByText(/规则：/).parentElement).toHaveTextContent(
      "RISK_CAP",
    );
  });
  it("loads a selected real chart range with a normal mobile-safe click", async () => {
    const fetch = vi.fn((input: string | URL | Request) => {
      const path = url(input);
      if (path.endsWith("/analyst-report")) return response(report);
      if (path.includes("/chart")) return response(chart);
      throw new Error(path);
    });
    vi.stubGlobal("fetch", fetch);
    renderPage();
    await screen.findByRole("heading", { name: "GOOGL" });
    await userEvent.click(screen.getByRole("button", { name: "3M" }));
    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining("/chart?range=3M"),
      expect.anything(),
    );
  });
  it("explains the deterministic sizing constraint and planned stop risk", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((input: string | URL | Request) => {
        const path = url(input);
        if (path.endsWith("/analyst-report"))
          return response({
            ...report,
            evidence: { ...report.evidence, exactQuantityAllowed: true },
            recommendation: {
              ...report.recommendation,
              action: "ADD",
              quantityMin: "10",
              quantityMax: "17",
            },
            layers: {
              ...report.layers,
              systemRecommendation: {
                ...report.layers.systemRecommendation,
                action: "ADD",
                exactQuantityAllowed: true,
                quantityMin: "10",
                quantityMax: "17",
                quantityBeforeLimitingConstraint: "30",
              },
              risk: {
                ...report.layers.risk,
                totalPortfolioRiskCap: "0.02",
                projectedPositionWeight: "0.11",
                projectedTotalRiskAfterAction: "0.0199",
                projectedClusterRiskAfterAction: "0.014",
                riskPerShare: "26.14",
              },
            },
          });
        if (path.includes("/chart")) return response(chart);
        throw new Error(path);
      }),
    );
    renderPage();
    await screen.findByText("最多建议 17 股");
    expect(screen.getByText("为什么不是 30 股？")).toBeInTheDocument();
    expect(screen.getByText(/交易后总计划风险 1.99% \/ 2.00%/)).toBeInTheDocument();
    expect(screen.getByText(/如果出现隔夜跳空/)).toBeInTheDocument();
    expect(screen.queryByText(/最大可能损失/)).not.toBeInTheDocument();
  });
  it("explains when estimate dispersion lowers forward valuation confidence", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((input: string | URL | Request) => {
        const path = url(input);
        if (path.endsWith("/analyst-report"))
          return response({
            ...report,
            layers: {
              ...report.layers,
              estimates: {
                ...report.layers.estimates,
                dispersion: "0.55",
                dispersionHigh: true,
              },
            },
          });
        if (path.includes("/chart")) return response(chart);
        throw new Error(path);
      }),
    );
    renderPage();
    expect(
      await screen.findByText(
        "分析师对盈利路径分歧较大，因此 forward valuation 置信度下降。",
      ),
    ).toBeInTheDocument();
  });
  it("shows unavailable instead of zero when canonical metrics are absent", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((input: string | URL | Request) => {
        const path = url(input);
        if (path.endsWith("/analyst-report"))
          return response({
            ...report,
            layers: {
              ...report.layers,
              fundamentals: {
                financialHealth: "MISSING",
                quality: "MISSING",
                available: false,
              },
              valuation: {
                state: "MISSING",
                confidence: "MISSING",
                observationCount: 0,
                independentConfirmation: false,
                attractive: false,
                attractiveButCannotAdd: false,
              },
              estimates: {
                state: "MISSING",
                quality: "MISSING",
              },
            },
          });
        if (path.includes("/chart")) return response(chart);
        throw new Error(path);
      }),
    );
    renderPage();
    await screen.findByRole("heading", { name: "GOOGL" });
    expect(screen.getAllByText("暂无可靠数据").length).toBeGreaterThan(5);
    expect(screen.queryByText("$0")).not.toBeInTheDocument();
  });
  it("uses a bounded speculative template without any execution control", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((input: string | URL | Request) => {
        const path = url(input);
        if (path.endsWith("/analyst-report"))
          return response({
            ...report,
            position: { ...report.position, classification: "SPECULATIVE" },
            assetEvidence: {
              speculative: {
                speculativePolicyApplied: true,
                hardMaxWeight: "0.02",
                confidenceCeiling: "LOW",
                tickerOrPriceCanUpgradeQuality: false,
              },
            },
            layers: {
              ...report.layers,
              portfolioRole: {
                ...report.layers.portfolioRole,
                classification: "SPECULATIVE",
                normalMaxWeight: "0.01",
                hardMaxWeight: "0.02",
              },
              risk: {
                ...report.layers.risk,
                currentWeight: "0.01",
                normalMaxWeight: "0.01",
                hardMaxWeight: "0.02",
              },
            },
          });
        if (path.includes("/chart")) return response(chart);
        throw new Error(path);
      }),
    );
    renderPage();
    expect(
      await screen.findByRole("heading", { name: "仓位边界" }),
    ).toBeInTheDocument();
    expect(screen.getByText("禁止摊低成本").parentElement).toHaveTextContent(
      "是",
    );
    expect(screen.queryByRole("button", { name: /交易|下单/ })).toBeNull();
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
