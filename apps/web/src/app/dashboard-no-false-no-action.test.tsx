import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { DashboardPage } from "./workspace-pages";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock("@portfolio/api-client", () => ({ api: { GET: get } }));

describe("DashboardNoFalseNoActionTest", () => {
  afterEach(cleanup);

  it("shows an analysis error instead of NO URGENT ACTION", async () => {
    get.mockImplementation((path?: string) => {
      if (path === "/api/v1/brief/today")
        return Promise.resolve({
          data: undefined,
          error: { detail: "offline" },
          response: new Response(null, { status: 503 }),
        });
      throw new Error(`Unexpected GET ${String(path)}`);
    });

    render(
      <QueryClientProvider
        client={
          new QueryClient({ defaultOptions: { queries: { retry: false } } })
        }
      >
        <DashboardPage />
      </QueryClientProvider>,
    );

    expect(await screen.findByRole("alert")).toHaveTextContent("无法加载分析");
    expect(screen.queryByText("NO URGENT ACTION")).not.toBeInTheDocument();
  });

  it("shows partial readiness instead of a false calm state", async () => {
    get.mockResolvedValueOnce({
      data: {
        state: "PARTIAL_ANALYSIS",
        headline: "Some positions have not completed analysis.",
        summary: { investedValue: "100", trackedCash: "0", openPositions: 2 },
        mustAct: [],
        doNot: [],
        watch: [],
        portfolioHealth: { status: "WARNING", reasons: [] },
        dataReadiness: {
          status: "PARTIAL",
          marketCoverage: "1",
          fundamentalCoverage: "0.5",
          stalePositionCount: 0,
          missingPositionCount: 1,
          failedJobCount: 0,
        },
        nextEvents: [],
      },
      response: new Response(),
    });

    render(
      <QueryClientProvider client={new QueryClient()}>
        <DashboardPage />
      </QueryClientProvider>,
    );

    expect(
      await screen.findAllByText("Some positions have not completed analysis."),
    ).not.toHaveLength(0);
    expect(screen.queryByText("NO URGENT ACTION")).not.toBeInTheDocument();
  });

  it("shows NO URGENT ACTION only for a ready empty action queue", async () => {
    get.mockResolvedValueOnce({
      data: {
        state: "ANALYSIS_READY",
        headline: "NO URGENT ACTION",
        summary: { investedValue: "100", trackedCash: "20", openPositions: 1 },
        mustAct: [],
        doNot: [],
        watch: [],
        portfolioHealth: { status: "HEALTHY", reasons: [] },
        dataReadiness: {
          status: "HEALTHY",
          marketCoverage: "1",
          fundamentalCoverage: "1",
          stalePositionCount: 0,
          missingPositionCount: 0,
          failedJobCount: 0,
        },
        nextEvents: [],
      },
      response: new Response(),
    });

    render(
      <QueryClientProvider client={new QueryClient()}>
        <DashboardPage />
      </QueryClientProvider>,
    );

    expect(await screen.findByText("当前无需紧急操作")).toBeInTheDocument();
  });
});
