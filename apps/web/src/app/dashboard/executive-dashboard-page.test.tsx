import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ExecutiveDashboardPage } from "./ExecutiveDashboardPage";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock("@portfolio/api-client", () => ({ api: { GET: get } }));

describe("ExecutiveDashboardPageTest", () => {
  afterEach(cleanup);
  it("shows only explicit import choices for an empty portfolio", async () => {
    get.mockResolvedValueOnce({
      data: {
        state: "NO_PORTFOLIO",
        headline: "No portfolio",
        summary: {
          investedValue: "0",
          trackedCash: "0",
          emergencyCash: "0",
          tacticalReserve: "0",
          openPositions: 0,
        },
        mustAct: [],
        doNot: [],
        watch: [],
        portfolioHealth: { status: "UNKNOWN", reasons: [] },
        dataReadiness: {
          status: "MISSING",
          marketCoverage: "0",
          fundamentalCoverage: "0",
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
        <ExecutiveDashboardPage />
      </QueryClientProvider>,
    );
    expect(
      await screen.findByRole("heading", { name: "尚未导入投资组合" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "导入 Fidelity CSV" }),
    ).toHaveAttribute("href", "/portfolio/import?mode=file");
    expect(
      screen.getByRole("link", { name: "粘贴持仓表" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "手动录入" })).toBeInTheDocument();
    expect(screen.queryByText(/NO URGENT ACTION/i)).not.toBeInTheDocument();
  });
});
