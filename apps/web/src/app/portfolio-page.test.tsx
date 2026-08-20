import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { PortfolioPage } from "./portfolio-page";
const rows = [
  {
    id: "p1",
    version: 2,
    symbol: "GOOGL",
    name: "Alphabet",
    assetType: "STOCK",
    bucket: "TACTICAL",
    classification: "QUALITY_STOCK",
    classificationConfirmed: true,
    marketValue: "5000.125",
    currentPrice: "196.14",
    averageCost: "172.02",
    unrealizedPnlDollar: "615.12",
    unrealizedPnlPct: "0.1402",
    dayChangePct: "0.012",
    oneMonthReturn: "0.053",
    currentWeight: "0.156",
    targetWeightMin: "0.08",
    targetWeightMax: "0.12",
    action: "HOLD_DO_NOT_ADD",
    priority: "DO_NOT",
    confidence: "MEDIUM",
    keyReason: "仓位超过策略目标区间",
    trend: "ABOVE_TREND",
    nextEvent: "2026-08-20T20:00:00Z",
    dataStatus: "READY",
  },
  {
    id: "p2",
    version: 0,
    symbol: "DXYZ",
    name: "Destiny Tech100",
    assetType: "STOCK",
    bucket: "SPECULATIVE",
    classification: "UNKNOWN",
    classificationConfirmed: false,
    marketValue: "900",
    currentWeight: "0.03",
    targetWeightMin: null,
    targetWeightMax: null,
    action: "WAIT_FOR_DATA",
    priority: "MUST_ACT",
    confidence: "WAIT_FOR_DATA",
    keyReason: null,
    trend: "WAIT_FOR_DATA",
    nextEvent: null,
    dataStatus: "WAIT_FOR_DATA",
  },
];
function renderPage() {
  return render(
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retry: false } } })
      }
    >
      <PortfolioPage />
    </QueryClientProvider>,
  );
}
function response(value: unknown) {
  return new Response(JSON.stringify(value), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });
}
function requestPath(input: string | URL | Request) {
  if (typeof input === "string") return input;
  return input instanceof URL ? input.href : input.url;
}
describe("PortfolioPage", () => {
  beforeEach(() => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation((input: string | URL | Request) =>
        Promise.resolve(
          response(
            requestPath(input).includes("/api/v1/brief/today")
              ? {
                  summary: { investedValue: "5900.125" },
                  capital: {
                    deployableCash: "1200",
                    emergencyReserve: "5000",
                  },
                  mustAct: [rows[1]],
                }
              : rows,
          ),
        ),
      ),
    );
  });
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });
  it("renders six decision columns in priority order without changing decimal data", async () => {
    renderPage();
    const symbols = await screen.findAllByRole("link", { name: /DXYZ|GOOGL/ });
    expect(symbols[0]).toHaveTextContent("DXYZ");
    expect(screen.getByText("Alphabet")).toBeInTheDocument();
    expect(screen.getByText(/5,000\.13/)).toBeInTheDocument();
    expect(screen.getByText("15.6%")).toBeInTheDocument();
    expect(screen.getByText(/浮盈亏 14\.0%/)).toBeInTheDocument();
    expect(screen.getByText(/现价 US\$196\.14/)).toBeInTheDocument();
    expect(screen.getByText("15.6%").closest("td")).toHaveTextContent(
      "8.0%–12.0%",
    );
    expect(screen.getByText("不要加仓")).toBeInTheDocument();
    expect(screen.queryByText("HOLD_DO_NOT_ADD")).not.toBeInTheDocument();
    expect(screen.getByText("仓位超过策略目标区间")).toBeInTheDocument();
    expect(screen.getAllByRole("columnheader")).toHaveLength(6);
    expect(screen.getByText(/5,900/)).toBeInTheDocument();
  });
  it("filters missing evidence honestly", async () => {
    renderPage();
    await screen.findByText("GOOGL");
    await userEvent.click(screen.getByRole("button", { name: "数据待补" }));
    expect(screen.getByText("DXYZ")).toBeInTheDocument();
    expect(screen.queryByText("GOOGL")).not.toBeInTheDocument();
  });
  it("searches by symbol and company name", async () => {
    renderPage();
    const search = await screen.findByRole("searchbox", { name: "搜索持仓" });
    await userEvent.type(search, "alphabet");
    expect(screen.getByText("GOOGL")).toBeInTheDocument();
    expect(screen.queryByText("DXYZ")).not.toBeInTheDocument();
  });
  it("opens classification from selected row only", async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockImplementation((input: string | URL | Request) => {
      const path = requestPath(input);
      if (path.includes("classification-suggestion")) {
        return Promise.resolve(
          response({
            positionId: "p2",
            symbol: "DXYZ",
            assetType: "STOCK",
            classification: "SPECULATIVE",
            source: "SYSTEM_RULE",
            blocked: false,
            reason: "Evidence matches policy.",
            confirmationRequired: true,
          }),
        );
      }
      return Promise.resolve(
        response(
          path.includes("/api/v1/brief/today")
            ? { summary: {}, capital: {}, mustAct: [] }
            : rows,
        ),
      );
    });
    renderPage();
    await userEvent.click(
      await screen.findByRole("button", { name: "确认组合角色" }),
    );
    expect(await screen.findByRole("dialog")).toHaveTextContent("DXYZ");
    expect(screen.queryByDisplayValue("GOOGL")).not.toBeInTheDocument();
  });
});
