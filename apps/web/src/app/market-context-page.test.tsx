import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MarketContextPage } from "./market-context-page";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));
const { getJson } = vi.hoisted(() => ({ getJson: vi.fn() }));
vi.mock("@portfolio/api-client", () => ({ api: { GET: get } }));
vi.mock("./http", () => ({ getJson }));

function renderPage() {
  return render(
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retry: false } } })
      }
    >
      <MarketContextPage />
    </QueryClientProvider>,
  );
}

describe("MarketContextPage", () => {
  afterEach(cleanup);
  beforeEach(() => {
    get.mockReset();
    getJson.mockImplementation((path: string) =>
      Promise.resolve(
        path.endsWith("/macro-background")
          ? {
              status: "READY",
              snapshot: {
                tenYearYield: "4.2",
                twoYearYield: "3.95",
                fedFundsRate: "4.5",
                tenYearRealYield: "1.8",
                rateStress: "0.45",
                curveState: "NORMAL",
                quality: "HEALTHY",
                includedInAggregateStress: false,
              },
              dataAsOf: "2026-08-05T00:00:00Z",
            }
          : {
              status: "READY",
              snapshot: {
                vix: "18.2",
                vixPercentile: "0.42",
                vixDelta1d: "0.5",
                vixDelta2d: "-0.2",
                vixDelta5d: "1.1",
                vix3m: "20",
                vixTermRatio: "0.91",
                vixTermState: "CONTANGO",
                techStressState: "MISSING",
                quality: "HEALTHY",
              },
              dataAsOf: "2026-08-05T00:00:00Z",
            },
      ),
    );
  });

  it("shows regime, market-driven drawdown, and stale blocking", async () => {
    get.mockImplementation((path?: string) => {
      const safePath = path ?? "";
      if (safePath.endsWith("/regime"))
        return Promise.resolve({
          data: {
            status: "READY",
            snapshot: {
              strategyVersion: "1.0.0-draft",
              label: "GREEN",
              score: 81,
              trendScore: 36,
              momentumScore: 17,
              breadthScore: 13,
              stressScore: 15,
              confidence: "LOW",
              qualityStatus: "STALE",
              narrativesJson: '["Stale evidence prevents STRONG_GREEN."]',
              dataAsOf: "2026-08-05T00:00:00Z",
            },
          },
          response: new Response(),
        });
      if (safePath.endsWith("/drawdown"))
        return Promise.resolve({
          data: {
            status: "READY",
            snapshot: {
              drawdownFraction: "0.15",
              drawdownPercent: "15",
              state: "ETF_DIP_MARKET_DRIVEN",
              sourceClassification: "MARKET_DRIVEN",
              marketDriven: true,
              spyReturnFromPeak: "-0.12",
              qqqReturnFromPeak: "-0.14",
              spyDrawdownPercent: "-12",
              qqqDrawdownPercent: "-14",
              highWaterMark: "100000",
              confidence: "HIGH",
            },
          },
          response: new Response(),
        });
      return Promise.resolve({
        data: {
          staleObservations: 2,
          partialObservations: 1,
          suspectObservations: 0,
          openQualityEvents: 0,
        },
        response: new Response(),
      });
    });
    renderPage();
    expect(await screen.findByText("GREEN")).toBeInTheDocument();
    expect(screen.getByText("15%")).toBeInTheDocument();
    expect(screen.getByText("MARKET_DRIVEN")).toBeInTheDocument();
    expect(screen.getByText("18.20")).toBeInTheDocument();
    expect(screen.getByText("CONTANGO")).toBeInTheDocument();
    expect(screen.getByText("1.80%")).toBeInTheDocument();
    expect(screen.getByText(/尚未计入 aggregate market stress score/)).toBeInTheDocument();
    expect(
      screen.getByText("暂无可靠 VXN 数据；广义市场判断仍可继续。"),
    ).toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent(
      "precise advice are blocked",
    );
  });

  it("shows the technology volatility overlay when reliable VXN exists", async () => {
    get.mockImplementation((path?: string) =>
      Promise.resolve({
        data: path?.endsWith("/regime")
          ? { status: "EMPTY" }
          : path?.endsWith("/drawdown")
            ? { status: "EMPTY" }
            : {
                staleObservations: 0,
                partialObservations: 0,
                suspectObservations: 0,
                openQualityEvents: 0,
              },
        response: new Response(),
      }),
    );
    getJson.mockImplementation((path: string) =>
      Promise.resolve(path.endsWith("/macro-background") ? {} : {
      status: "READY",
      snapshot: {
        vix: "20",
        vix3m: "19",
        vixTermRatio: "1.0526",
        vixTermState: "BACKWARDATION",
        vxn: "27",
        vxnPercentile: "0.8",
        vxnVixRatio: "1.35",
        vxnVixSpread: "7",
        techStressState: "ELEVATED",
        quality: "HEALTHY",
      },
    }));
    renderPage();
    expect(await screen.findByText("27.00")).toBeInTheDocument();
    expect(screen.getByText("1.35")).toBeInTheDocument();
    expect(screen.getByText("ELEVATED")).toBeInTheDocument();
  });

  it("uses honest empty and authentication-required states", async () => {
    get.mockImplementation((path?: string) => {
      const safePath = path ?? "";
      if (safePath.endsWith("/regime"))
        return Promise.resolve({
          data: { status: "EMPTY" },
          response: new Response(),
        });
      if (safePath.endsWith("/drawdown"))
        return Promise.resolve({
          data: undefined,
          error: { detail: "Unauthorized" },
          response: new Response(null, { status: 401 }),
        });
      return Promise.resolve({
        data: {
          staleObservations: 0,
          partialObservations: 0,
          suspectObservations: 0,
          openQualityEvents: 0,
        },
        response: new Response(),
      });
    });
    renderPage();
    expect(
      await screen.findByText("No versioned regime snapshot. WAIT_FOR_DATA."),
    ).toBeInTheDocument();
    expect(
      await screen.findByText(
        "Sign in is required to inspect private portfolio drawdown.",
      ),
    ).toBeInTheDocument();
  });
});
