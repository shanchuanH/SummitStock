import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { DashboardPage, ReviewPage, SettingsPage } from "./workspace-pages";

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
          requiredEmergencyFloor: "1000",
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

  it("renders cashflow-adjusted performance and rule-objective outcomes", async () => {
    get.mockResolvedValueOnce(await ok([]));
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            quality: "HEALTHY",
            periods: [
              {
                period: "1Y",
                portfolioTwr: "0.12",
                spyReturn: "0.09",
                qqqReturn: "0.1",
                activeReturn: "0.06",
              },
            ],
            contributions: [{ symbol: "GOOGL", contribution: "0.032" }],
            decisionOutcomes: [
              {
                recommendationId: "r1",
                symbol: "GOOGL",
                action: "HOLD_DO_NOT_ADD",
                ruleObjective: "CONTROL_CONCENTRATION",
                evaluation: "OBJECTIVE_MAINTAINED",
                interpretation: "rule objective",
              },
            ],
            activeSleeve: {
              reviewMonths: 12,
              activeReturn: "0.06",
              benchmarkReturn: "0.1",
              relativeReturn: "-0.04",
              contribution: "0.012",
              activeMaxDrawdown: "0.12",
              coreMaxDrawdown: "0.1",
              turnover: "0.25",
              budgetMultiplier: "1",
              performanceQuality: "APPROXIMATE",
              ruleIds: [
                "ACTIVE_12M",
                "ACTIVE_SLEEVE_APPROXIMATION_NO_AUTO_MULTIPLIER",
              ],
            },
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
      ),
    );
    renderPage(<ReviewPage />);
    expect(await screen.findAllByText("12.0%")).toHaveLength(2);
    expect(screen.getByText(/GOOGL · 3.20%pp/)).toBeInTheDocument();
    expect(screen.getByText(/CONTROL_CONCENTRATION/)).toBeInTheDocument();
    expect(screen.getByText("APPROXIMATE")).toBeInTheDocument();
    expect(screen.getByText("1")).toBeInTheDocument();
  });
});
