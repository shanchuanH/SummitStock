import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { PortfolioPage } from "./portfolio-page";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
vi.mock("@portfolio/api-client", () => ({ api: { GET: get, POST: post } }));

describe("PortfolioPageNoWrongClassificationTest", () => {
  afterEach(cleanup);

  it("cannot apply a free-form DXYZ suggestion to the first position", async () => {
    get.mockImplementation((path: string) => {
      if (path.endsWith("/summary"))
        return Promise.resolve({
          data: {
            investedValue: "100",
            trackedCash: "0",
            openPositions: 1,
            dataAsOf: null,
          },
          response: new Response(),
        });
      if (path.endsWith("/positions"))
        return Promise.resolve({
          data: [
            {
              id: "first-position",
              symbol: "SPY",
              classification: "UNKNOWN",
              classificationConfirmed: false,
            },
          ],
          response: new Response(),
        });
      if (path.endsWith("/today"))
        return Promise.resolve({
          data: { mustAct: [], doNot: [], watch: [] },
          response: new Response(),
        });
      throw new Error(`Unexpected GET ${path}`);
    });

    render(
      <QueryClientProvider client={new QueryClient()}>
        <PortfolioPage />
      </QueryClientProvider>,
    );

    await screen.findByText("SPY");
    expect(screen.queryByDisplayValue("DXYZ")).not.toBeInTheDocument();
    expect(
      screen.queryByText(/Confirm for first position/i),
    ).not.toBeInTheDocument();
    expect(post).not.toHaveBeenCalled();
  });
});
