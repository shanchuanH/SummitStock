import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MarketInspectionPage } from "./market-inspection-page";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock("@portfolio/api-client", () => ({ api: { GET: get } }));

function responseFor(path: string) {
  if (path.endsWith("data-health"))
    return {
      status: "HEALTHY",
      activeInstruments: 2,
      priceBars: 84,
      indicatorSnapshots: 8,
      openQualityEvents: 0,
      dataAsOf: "2026-08-05T00:00:00Z",
    };
  if (path === "/api/v1/instruments")
    return { items: [{ id: "1", symbol: "SPY" }] };
  if (path.endsWith("/bars"))
    return {
      items: [
        {
          marketDate: "2026-08-04",
          open: "100",
          high: "102",
          low: "99",
          close: "101",
          qualityStatus: "VALID",
        },
      ],
    };
  if (path.endsWith("/indicators"))
    return {
      items: [
        {
          marketDate: "2026-08-04",
          indicatorCode: "RSI_14",
          status: "READY",
          value: 61.25,
        },
      ],
    };
  return { items: [] };
}

function renderPage() {
  return render(
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retry: false } } })
      }
    >
      <MarketInspectionPage />
    </QueryClientProvider>,
  );
}

describe("MarketInspectionPage", () => {
  afterEach(cleanup);

  beforeEach(() => {
    get.mockReset();
    get.mockImplementation((path: string) =>
      Promise.resolve({ data: responseFor(path), response: new Response() }),
    );
  });

  it("shows traceable bars, indicators and data health", async () => {
    renderPage();
    expect(await screen.findByText("HEALTHY")).toBeInTheDocument();
    expect(screen.getByText("101")).toBeInTheDocument();
    expect(screen.getByText("RSI_14")).toBeInTheDocument();
    expect(screen.getByText("61.2500")).toBeInTheDocument();
  });

  it("normalizes a searched symbol before querying", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.clear(screen.getByLabelText("Instrument symbol"));
    await user.type(screen.getByLabelText("Instrument symbol"), " qqq ");
    await user.click(screen.getByRole("button", { name: /inspect/i }));
    expect(
      await screen.findByText("QQQ adjusted daily bars"),
    ).toBeInTheDocument();
  });
});
