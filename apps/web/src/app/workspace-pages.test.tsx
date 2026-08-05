import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { DashboardPage, SettingsPage } from "./workspace-pages";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock("@portfolio/api-client", () => ({ api: { GET: get } }));
const ok = (data: unknown) =>
  Promise.resolve({ data, response: new Response() });
function renderPage(page: React.ReactNode) {
  return render(
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retry: false } } })
      }
    >
      {page}
    </QueryClientProvider>,
  );
}

describe("Packet 07 workspace", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });
  beforeEach(() => get.mockReset());

  it("shows the calm state and never renders more than three urgent actions", async () => {
    get
      .mockResolvedValueOnce(
        await ok({ authenticated: true, username: "owner@example.local" }),
      )
      .mockResolvedValueOnce(
        await ok({
          mustAct: [1, 2, 3, 4].map((n) => ({
            id: String(n),
            symbol: `S${String(n)}`,
            action: "REVIEW",
            confidence: "HIGH",
          })),
          doNot: [],
          watch: [],
        }),
      );
    renderPage(<DashboardPage />);
    expect(await screen.findByText("S1")).toBeInTheDocument();
    expect(screen.getAllByText("REVIEW")).toHaveLength(3);
    expect(screen.queryByText("S4")).not.toBeInTheDocument();
  });

  it("states the session-cookie policy and includes the CSRF form token", async () => {
    get
      .mockResolvedValueOnce(await ok({ authenticated: false, username: null }))
      .mockResolvedValueOnce(
        await ok({
          headerName: "X-CSRF-TOKEN",
          parameterName: "_csrf",
          token: "safe-token",
        }),
      );
    renderPage(<SettingsPage />);
    expect(
      await screen.findByText(/No access token is stored/i),
    ).toBeInTheDocument();
    expect(
      (await screen.findByDisplayValue("safe-token")).getAttribute("name"),
    ).toBe("_csrf");
  });

  it("submits login without navigating to the API error page", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            headerName: "X-CSRF-TOKEN",
            parameterName: "_csrf",
            token: "fresh-token",
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
      )
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);
    get.mockImplementation((path?: string) =>
      path === "/api/v1/auth/session"
        ? ok({
            authenticated: fetchMock.mock.calls.length >= 2,
            username:
              fetchMock.mock.calls.length >= 2
                ? "admin@example.local"
                : null,
          })
        : ok({
            headerName: "X-CSRF-TOKEN",
            parameterName: "_csrf",
            token: "render-token",
          }),
    );

    renderPage(<SettingsPage />);
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText("Email"), "admin@example.local");
    await user.type(screen.getByLabelText("Password"), "change-before-use");
    await user.click(screen.getByRole("button", { name: "Sign in" }));

    expect(
      await screen.findByRole("heading", { name: "Authenticated session" }),
    ).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[1]?.[0]).toBe("/api/v1/auth/login");
  });
});
