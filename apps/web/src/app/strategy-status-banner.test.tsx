import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { StrategyStatusBanner } from "./strategy-status-banner";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock("@portfolio/api-client", () => ({ api: { GET: get } }));

function renderBanner() {
  return render(
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retry: false } } })
      }
    >
      <StrategyStatusBanner />
    </QueryClientProvider>,
  );
}

describe("StrategyStatusBanner", () => {
  beforeEach(() => get.mockReset());
  afterEach(cleanup);

  it("visibly marks an explicit draft runtime override", async () => {
    get.mockResolvedValue({
      data: {
        strategyVersion: "2.0.0-draft",
        strategyPublishState: "MISSING",
        productionStrategy: false,
        draftStrategyOverride: true,
      },
      response: new Response(),
    });

    renderBanner();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "DRAFT STRATEGY / NOT PRODUCTION",
    );
  });

  it("does not show a warning for a verified published strategy", async () => {
    get.mockResolvedValue({
      data: {
        strategyVersion: "2.0.0",
        strategyPublishState: "PUBLISHED",
        productionStrategy: true,
        draftStrategyOverride: false,
      },
      response: new Response(),
    });

    const view = renderBanner();

    await vi.waitFor(() => {
      expect(get).toHaveBeenCalled();
    });
    expect(view.container).toBeEmptyDOMElement();
  });
});
