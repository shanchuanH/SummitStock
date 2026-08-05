import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ClassificationModal } from "./classification-modal";

describe("ClassificationModalTest", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });
  it("shows evidence and submits the selected position id and version", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            positionId: "position-dxyz",
            symbol: "DXYZ",
            assetType: "STOCK",
            classification: "SPECULATIVE",
            source: "SYSTEM_RULE",
            blocked: false,
            reason: "Limited evidence and speculative structure.",
            confirmationRequired: true,
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({ headerName: "X-CSRF-TOKEN", token: "csrf" }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ id: "position-dxyz" }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }),
      );
    vi.stubGlobal("fetch", fetchMock);
    render(
      <QueryClientProvider client={new QueryClient()}>
        <ClassificationModal
          positionId="position-dxyz"
          version={7}
          onClose={vi.fn()}
        />
      </QueryClientProvider>,
    );
    expect(
      await screen.findByText("Limited evidence and speculative structure."),
    ).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "确认分类" }));
    expect(fetchMock).toHaveBeenNthCalledWith(
      3,
      "/api/v1/positions/position-dxyz/classify",
      expect.objectContaining({
        body: JSON.stringify({
          classification: "SPECULATIVE",
          expectedVersion: 7,
        }),
      }),
    );
  });
});
