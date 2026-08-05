import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { HealthPage } from "./health-page";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock("@portfolio/api-client", () => ({ api: { GET: get } }));

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <HealthPage />
    </QueryClientProvider>,
  );
}

describe("HealthPage", () => {
  afterEach(cleanup);

  beforeEach(() => get.mockReset());

  it("shows generated API version data", async () => {
    get.mockResolvedValue({
      data: {
        version: "0.1.0",
        strategyVersion: "1.0.0-draft",
        runtimeMode: "api",
        dataAsOf: "2026-08-05T00:00:00Z",
        ruleIds: [],
      },
      response: new Response(),
    });
    renderPage();
    expect(await screen.findByText("READY")).toBeInTheDocument();
    expect(screen.getByText("MYSQL 8.4")).toBeInTheDocument();
  });

  it("uses an honest unavailable state", async () => {
    get.mockResolvedValue({
      data: undefined,
      error: { detail: "offline" },
      response: new Response(null, { status: 503 }),
    });
    renderPage();
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "No investment data or recommendation has been fabricated",
    );
  });
});
