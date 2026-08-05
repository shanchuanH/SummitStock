import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { BacktestPage } from "./backtest-page";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock("@portfolio/api-client", () => ({ api: { GET: get } }));
function renderPage() {
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <BacktestPage />
    </QueryClientProvider>,
  );
}

describe("BacktestPage", () => {
  afterEach(cleanup);
  beforeEach(() => get.mockReset());
  it("renders verified OOS decision metrics", async () => {
    get.mockResolvedValue({
      data: {
        strategyVersion: "1.0.0-draft",
        periodStart: "2020-01-01",
        periodEnd: "2025-12-31",
        biasStatus: "CLEAR",
        outOfSampleFrom: "2024-01-01",
        metrics: [
          {
            name: "HOLD_FREQUENCY",
            value: 0.72,
            sampleCount: 100,
            sleeve: "ALL",
            horizonDays: -1,
          },
        ],
      },
      response: new Response(),
    });
    renderPage();
    expect(await screen.findByText("HOLD_FREQUENCY")).toBeInTheDocument();
    expect(screen.getByText("0.7200")).toBeInTheDocument();
    expect(screen.getByText("CLEAR")).toBeInTheDocument();
  });
  it("uses an honest empty state", async () => {
    get.mockResolvedValue({
      data: undefined,
      response: new Response(null, { status: 404 }),
    });
    renderPage();
    expect(await screen.findByText("NO VERIFIED REPORT")).toBeInTheDocument();
  });
});
