import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { PositionDetailPage } from "./position-detail-page";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock("@portfolio/api-client", () => ({ api: { GET: get } }));

function ok(data: unknown) {
  return Promise.resolve({ data, response: new Response() });
}
function renderPage() {
  return render(
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retry: false } } })
      }
    >
      <MemoryRouter
        initialEntries={["/positions/cccccccc-cccc-cccc-cccc-cccccccccccc"]}
      >
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
  afterEach(cleanup);
  beforeEach(() => get.mockReset());

  it("shows stop, thesis, valuation, earnings, and risk journal evidence", async () => {
    get.mockImplementation((path?: string) =>
      path?.endsWith("intelligence") === true
        ? ok({
            stop: {
              entryPrice: "100",
              initialStop: "90",
              liveStop: "94",
              softAlert: "96",
              catastrophicStop: "91",
              qualityStatus: "HEALTHY",
            },
            thesis: {
              status: "HEALTHY",
              summary: "Margins remain durable",
              userConfirmed: true,
              expiresAt: "2027-01-01T00:00:00Z",
              sourcesJson: '["filing"]',
            },
            valuation: {
              action: "ADD_1_PERCENT_STARTER",
              fundamentalHealth: "HEALTHY",
              earningsRevisions: "IMPROVING",
              priceStabilization: "CONFIRMED",
              discountTacticalWeight: "0",
            },
            earnings: {
              action: "HOLD_THROUGH_EVENT",
              eventCount: 10,
              gapP90Fraction: "0.1",
              profitCushionR: "1.5",
              nextEventAt: "2026-09-01T00:00:00Z",
            },
            journal: [
              {
                id: "1",
                entryType: "INITIAL",
                taxStatus: "LONG_TERM",
                realizedR: "1.5",
                mfeR: "2.5",
                maeR: "-0.5",
                exitReason: "TARGET",
              },
            ],
          })
        : ok({ symbol: "SPY", classification: "QUALITY_STOCK" }),
    );
    renderPage();
    expect(
      await screen.findByText("Margins remain durable"),
    ).toBeInTheDocument();
    expect(screen.getByText("LIVE STOP 94")).toBeInTheDocument();
    expect(screen.getByText("ADD_1_PERCENT_STARTER")).toBeInTheDocument();
    expect(screen.getByText("HOLD_THROUGH_EVENT")).toBeInTheDocument();
    expect(screen.getByText(/Realized 1.5R/)).toBeInTheDocument();
  });

  it("uses honest empty states without inventing analysis", async () => {
    get.mockImplementation((path?: string) =>
      path?.endsWith("intelligence") === true
        ? ok({ journal: [] })
        : ok({ symbol: "QQQ", classification: "CORE_TECH_ETF" }),
    );
    renderPage();
    expect(
      await screen.findByText(/no ordinary stock stop/i),
    ).toBeInTheDocument();
    expect(screen.getByText(/No structured thesis/)).toBeInTheDocument();
    expect(
      screen.getByText("8–12 historical events required."),
    ).toBeInTheDocument();
  });

  it("surfaces stale evidence and authentication errors", async () => {
    get.mockImplementation((path?: string) =>
      path?.endsWith("intelligence") === true
        ? ok({
            stop: {
              entryPrice: "100",
              initialStop: "90",
              liveStop: "94",
              softAlert: "96",
              catastrophicStop: "91",
              qualityStatus: "STALE",
            },
            journal: [],
          })
        : ok({ symbol: "DXYZ", classification: "SPECULATIVE" }),
    );
    const first = renderPage();
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "no precise execution quantity",
    );
    first.unmount();
    get.mockResolvedValue({
      data: undefined,
      error: { detail: "Unauthorized" },
      response: new Response(null, { status: 401 }),
    });
    renderPage();
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Position unavailable",
    );
  });
});
