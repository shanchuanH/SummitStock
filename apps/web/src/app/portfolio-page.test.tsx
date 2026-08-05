import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { PortfolioPage } from "./portfolio-page";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
vi.mock("@portfolio/api-client", () => ({ api: { GET: get, POST: post } }));

function ok(data: unknown) {
  return Promise.resolve({ data, response: new Response() });
}

function renderPage() {
  return render(
    <QueryClientProvider
      client={
        new QueryClient({
          defaultOptions: {
            queries: { retry: false },
            mutations: { retry: false },
          },
        })
      }
    >
      <PortfolioPage />
    </QueryClientProvider>,
  );
}

function mockPrivateData() {
  get.mockImplementation((path: string) => {
    if (path.endsWith("/summary"))
      return ok({
        investedValue: "5000.125",
        trackedCash: "2500.12",
        openPositions: 1,
        dataAsOf: "2026-08-05T00:00:00Z",
      });
    if (path.endsWith("/positions"))
      return ok([
        {
          id: "cccccccc-cccc-cccc-cccc-cccccccccccc",
          symbol: "SPY",
          bucket: "CORE",
          classification: "UNKNOWN",
          classificationConfirmed: false,
          marketValue: "5000.125",
          version: 0,
        },
      ]);
    if (path.endsWith("/today"))
      return ok({
        mustAct: [
          { id: "1", symbol: "SPY", action: "REVIEW" },
          { id: "2", symbol: "QQQ", action: "TRIM" },
          { id: "3", symbol: "NVDA", action: "CHECK" },
        ],
        doNot: [{ id: "4", symbol: "DXYZ", action: "AVERAGE_DOWN" }],
        watch: [],
      });
    if (path.endsWith("/csrf"))
      return ok({ headerName: "X-CSRF-TOKEN", token: "test-token" });
    if (path.endsWith("classification-suggestion"))
      return ok({
        classification: "UNKNOWN",
        blocked: false,
        reason: "Stock quality cannot be inferred from ticker or price action.",
        confirmationRequired: true,
      });
    throw new Error(`Unexpected GET ${path}`);
  });
}

describe("PortfolioPage", () => {
  afterEach(cleanup);
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
  });

  it("renders exact decimal strings, positions, and the capped action groups", async () => {
    mockPrivateData();
    renderPage();
    expect(await screen.findAllByText("5000.125")).toHaveLength(2);
    expect(screen.getByText("CONFIRM REQUIRED")).toBeInTheDocument();
    expect(screen.getByText("MUST ACT")).toBeInTheDocument();
    expect(screen.getAllByText(/REVIEW|TRIM|CHECK/)).toHaveLength(3);
  });

  it("does not infer quality for DXYZ", async () => {
    mockPrivateData();
    renderPage();
    const button = await screen.findByRole("button", {
      name: "Suggest classification",
    });
    const form = button.closest("form");
    if (form === null) throw new Error("Classification form not found");
    fireEvent.submit(form);
    expect(await screen.findAllByText("UNKNOWN")).toHaveLength(2);
    expect(screen.getByText(/cannot be inferred/)).toBeInTheDocument();
  });

  it("shows stale evidence without a precise quantity", async () => {
    mockPrivateData();
    post.mockImplementation(() =>
      ok({
        allowed: true,
        preciseQuantityAllowed: false,
        confidence: "LOW",
        ruleIds: ["DATA.STALE.002"],
      }),
    );
    renderPage();
    await userEvent.click(
      await screen.findByRole("button", { name: "Preview stale-data plan" }),
    );
    expect(
      await screen.findByText(/Exact quantity unavailable/),
    ).toBeInTheDocument();
    expect(screen.getByText("DATA.STALE.002")).toBeInTheDocument();
  });

  it("uses an explicit authentication-required state", async () => {
    get.mockResolvedValue({
      data: undefined,
      error: { detail: "Unauthorized" },
      response: new Response(null, { status: 401 }),
    });
    renderPage();
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Sign in required",
    );
  });
});
