import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  DashboardPage,
  OpportunitiesPage,
  ReviewPage,
  SettingsPage,
} from "./workspace-pages";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock("@portfolio/api-client", () => ({ api: { GET: get } }));
const ok = (data: unknown) =>
  Promise.resolve({ data, response: new Response() });
function renderPage(page: React.ReactNode) {
  return render(
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retry: false } } })
      }
    >
      {page}
    </QueryClientProvider>,
  );
}

describe("Packet 07 workspace", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });
  beforeEach(() => get.mockReset());

  it("shows the calm state and never renders more than three urgent actions", async () => {
    get.mockResolvedValueOnce(
      await ok({
        state: "ANALYSIS_READY",
        headline: "4 item(s) require action; 0 item(s) require watching.",
        summary: {
          investedValue: "10000",
          trackedCash: "2000",
          emergencyCash: "1000",
          tacticalReserve: "1000",
          openPositions: 4,
        },
        mustAct: [1, 2, 3, 4].map((n) => ({
          id: String(n),
          symbol: `S${String(n)}`,
          action: "REVIEW",
          priority: "MUST_ACT",
          confidence: "HIGH",
        })),
        doNot: [],
        watch: [],
        opportunities: [],
        blocked: [],
        todayPriorities: [1, 2, 3].map((n) => ({
          id: String(n),
          symbol: `S${String(n)}`,
          action: "REVIEW",
          priority: "MUST_ACT",
          confidence: "HIGH",
        })),
        topRisks: [],
        allHoldings: [],
        market: {
          regime: "YELLOW",
          score: 52,
          confidence: "MEDIUM",
          qualityStatus: "HEALTHY",
          summary: "Balanced",
          dataAsOf: "2026-08-05T20:00:00Z",
        },
        capital: {
          totalLiquidAssets: "12000",
          emergencyReserve: "1000",
          deployableCash: "1000",
          investableAssets: "11000",
          tacticalReserve: "1000",
        },
        portfolio: {
          drawdown: "0.02",
          drawdownSource: "MARKET_DRIVEN",
          technologyExposure: "0.4",
          openRisk: "0.01",
          clusterRisk: "0.02",
        },
        portfolioHealth: { status: "WARNING", reasons: [] },
        dataReadiness: {
          status: "HEALTHY",
          marketCoverage: "1",
          fundamentalCoverage: "1",
          completeness: "1",
          stalePositionCount: 0,
          missingPositionCount: 0,
          failedJobCount: 0,
        },
        nextEvents: [],
        strategyVersion: "1.0.0-draft",
      }),
    );
    renderPage(<DashboardPage />);
    expect(await screen.findAllByText("S1")).toHaveLength(2);
    expect(screen.getAllByText("需要复核后再决定")).toHaveLength(3);
    expect(screen.queryByText("S4")).not.toBeInTheDocument();
    expect(get).toHaveBeenCalledTimes(1);
    expect(get).toHaveBeenCalledWith("/api/v1/brief/today");
  });

  it("states the session-cookie policy and includes the CSRF form token", async () => {
    get
      .mockResolvedValueOnce(await ok({ authenticated: false, username: null }))
      .mockResolvedValueOnce(
        await ok({
          headerName: "X-CSRF-TOKEN",
          parameterName: "_csrf",
          token: "safe-token",
        }),
      );
    renderPage(<SettingsPage />);
    expect(
      await screen.findByText(/No access token is stored/i),
    ).toBeInTheDocument();
    expect(
      (await screen.findByDisplayValue("safe-token")).getAttribute("name"),
    ).toBe("_csrf");
  });

  it("submits login without navigating to the API error page", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            headerName: "X-CSRF-TOKEN",
            parameterName: "_csrf",
            token: "fresh-token",
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
      )
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);
    get.mockImplementation((path?: string) =>
      path === "/api/v1/auth/session"
        ? ok({
            authenticated: fetchMock.mock.calls.length >= 2,
            username:
              fetchMock.mock.calls.length >= 2 ? "admin@example.local" : null,
          })
        : ok({
            headerName: "X-CSRF-TOKEN",
            parameterName: "_csrf",
            token: "render-token",
          }),
    );

    renderPage(<SettingsPage />);
    const user = userEvent.setup();
    await user.type(
      await screen.findByLabelText("Email"),
      "admin@example.local",
    );
    await user.type(screen.getByLabelText("Password"), "change-before-use");
    await user.click(screen.getByRole("button", { name: "Sign in" }));

    expect(
      await screen.findByRole("heading", { name: "Authenticated session" }),
    ).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[1]?.[0]).toBe("/api/v1/auth/login");
  });

  it("shows one opportunity conclusion and deterministic ETF dip evidence", async () => {
    get
      .mockResolvedValueOnce(
        await ok({
          state: "ANALYSIS_READY",
          opportunities: [],
          blocked: [],
          watch: [],
          capital: { deployableCash: "20000" },
          portfolio: { openRisk: "0.018", clusterRisk: "0.025" },
          dataReadiness: { completeness: "1" },
        }),
      )
      .mockResolvedValueOnce(
        await ok({
          status: "READY",
          event: {
            symbol: "QQQM",
            status: "SETUP",
            setupScore: 68,
            triggerCount: 1,
            triggerCodesJson: '["RSI_CROSS_40"]',
            portfolioDrawdown: "0.08",
            instrumentDrawdown: "0.12",
            marketDriven: true,
            emergencyCashProtected: true,
            quality: "HEALTHY",
            reserveBefore: "10000",
            reserveAfter: "10000",
          },
        }),
      );
    renderPage(<OpportunitiesPage />);
    expect(
      await screen.findByText("出现核心 ETF 回撤观察机会"),
    ).toBeInTheDocument();
    expect(screen.getByText(/当前设置分：68 \/ 100/)).toBeInTheDocument();
    expect(screen.getByText("因此：暂不部署下一档。")).toBeInTheDocument();
    expect(screen.getByText(/RSI 重新站上 40/)).toBeInTheDocument();
  });

  it("reviews outcomes without declaring a recommendation right or wrong", async () => {
    get.mockResolvedValueOnce(
      await ok([
        {
          symbol: "GOOGL",
          action: "HOLD_DO_NOT_ADD",
          priority: "DO_NOT",
          decisionType: "DEFERRED",
          acknowledgedAt: "2026-08-03T20:00:00Z",
          decisionPrice: "200",
          currentPrice: "206.4",
          initialWeight: "0.138",
          currentWeight: "0.144",
        },
      ]),
    );
    renderPage(<ReviewPage />);
    expect(await screen.findByText(/价格 3.2%/)).toBeInTheDocument();
    expect(screen.getByText(/之后涨跌不代表原建议对错/)).toBeInTheDocument();
    expect(screen.queryByText(/^策略评价：建议(正确|错误)$/)).not.toBeInTheDocument();
  });
});
