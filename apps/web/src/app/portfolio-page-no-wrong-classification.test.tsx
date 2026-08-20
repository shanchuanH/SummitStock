import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { PortfolioPage } from "./portfolio-page";

describe("PortfolioPageNoWrongClassificationTest", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });
  it("cannot apply a free-form DXYZ symbol to another position", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          new Response(
            JSON.stringify([
              {
                id: "first-position",
                version: 0,
                symbol: "SPY",
                name: "SPDR S&P 500 ETF",
                assetType: "ETF",
                bucket: "CORE",
                classification: "UNKNOWN",
                classificationConfirmed: false,
                marketValue: "100",
                currentWeight: "1",
                action: "WAIT_FOR_DATA",
                priority: "WATCH",
                confidence: "WAIT_FOR_DATA",
                trend: "WAIT_FOR_DATA",
                dataStatus: "WAIT_FOR_DATA",
              },
            ]),
            { status: 200, headers: { "Content-Type": "application/json" } },
          ),
        ),
    );
    render(
      <QueryClientProvider client={new QueryClient()}>
        <PortfolioPage />
      </QueryClientProvider>,
    );
    await screen.findByText("SPY");
    expect(screen.queryByDisplayValue("DXYZ")).not.toBeInTheDocument();
    expect(fetch).toHaveBeenCalledTimes(2);
  });
});
