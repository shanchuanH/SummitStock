import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { DashboardPage } from "./workspace-pages";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock("@portfolio/api-client", () => ({ api: { GET: get } }));

describe("DashboardNoFalseNoActionTest", () => {
  afterEach(cleanup);

  it("shows an analysis error instead of NO URGENT ACTION", async () => {
    get.mockImplementation((path?: string) => {
      if (path === "/api/v1/auth/session")
        return Promise.resolve({
          data: { authenticated: true, username: "owner@example.local" },
          response: new Response(),
        });
      if (path === "/api/v1/portfolio/summary")
        return Promise.resolve({
          data: { openPositions: 1 },
          response: new Response(),
        });
      if (path === "/api/v1/actions/today")
        return Promise.resolve({
          data: undefined,
          error: { detail: "offline" },
          response: new Response(null, { status: 503 }),
        });
      throw new Error(`Unexpected GET ${String(path)}`);
    });

    render(
      <QueryClientProvider
        client={
          new QueryClient({ defaultOptions: { queries: { retry: false } } })
        }
      >
        <DashboardPage />
      </QueryClientProvider>,
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Unable to load portfolio analysis",
    );
    expect(screen.queryByText("NO URGENT ACTION")).not.toBeInTheDocument();
  });
});
