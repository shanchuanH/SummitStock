import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { DipPage } from "./dip-page";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
vi.mock("@portfolio/api-client", () => ({ api: { GET: get, POST: post } }));
const ok = (data: unknown) =>
  Promise.resolve({ data, response: new Response() });
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
      <DipPage />
    </QueryClientProvider>,
  );
}

describe("DipPage", () => {
  afterEach(cleanup);
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
  });
  it("shows setup evidence, four tranches, and history", async () => {
    get.mockImplementation((path?: string) =>
      path?.endsWith("status") === true
        ? ok({
            status: "READY",
            event: {
              symbol: "QQQ",
              status: "SETUP",
              setupScore: 76,
              triggerCount: 2,
              portfolioDrawdown: "0.15",
              marketDriven: true,
            },
          })
        : ok([
            {
              action: "DEPLOY_TRANCHE",
              symbol: "QQQ",
              confidence: "HIGH",
              status: "ACTIVE",
            },
          ]),
    );
    renderPage();
    expect(await screen.findByText("76.0")).toBeInTheDocument();
    expect(
      screen.getByText("DISLOCATION / SETUP").closest("article"),
    ).toHaveTextContent("2 triggers");
    expect(screen.getAllByText(/20%|25%|30%/)).toHaveLength(4);
    expect(screen.getByText(/DEPLOY_TRANCHE/)).toBeInTheDocument();
  });
  it("uses an honest no-action state and previews no-signal cash fallback", async () => {
    get.mockImplementation((path?: string) =>
      path?.endsWith("csrf") === true
        ? ok({ headerName: "X-CSRF-TOKEN", token: "token" })
        : path?.endsWith("status") === true
          ? ok({ status: "EMPTY" })
          : ok([]),
    );
    post.mockReturnValue(
      ok({
        broadCore: "4200",
        techCore: "1050",
        internationalCore: "700",
        tacticalReserve: "1050",
        qualityOpportunity: "0",
      }),
    );
    renderPage();
    expect(await screen.findByText(/NO ACTION is normal/)).toBeInTheDocument();
    await userEvent.click(
      screen.getByRole("button", { name: "Preview no-signal allocation" }),
    );
    expect(await screen.findByText("4200")).toBeInTheDocument();
  });
  it("requires authentication", async () => {
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
