import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { PortfolioImportPage } from "./PortfolioImportPage";

const preview = {
  batchId: "11111111-1111-1111-1111-111111111111",
  status: "PREVIEW",
  version: 0,
  accounts: [
    { accountName: "Primary Brokerage", accountNumberMasked: "***5678" },
  ],
  holdings: [
    {
      rowNumber: 2,
      accountName: "Primary Brokerage",
      accountNumberMasked: "***5678",
      symbol: "SPY",
      description: "SPDR ETF",
      assetType: "ETF",
      quantity: "10.5",
      currentValue: "5400.05",
      costBasis: "5250",
      rowType: "HOLDING",
      status: "VALID",
      warnings: [],
    },
    {
      rowNumber: 3,
      accountName: "Primary Brokerage",
      accountNumberMasked: "***5678",
      description: "Unknown private asset",
      quantity: "1",
      currentValue: "12",
      rowType: "UNKNOWN",
      status: "ERROR",
      warnings: [],
    },
  ],
  cash: [
    {
      rowNumber: 4,
      accountName: "Primary Brokerage",
      accountNumberMasked: "***5678",
      symbol: "SPAXX",
      currentValue: "14000",
      rowType: "CASH",
      status: "VALID",
      warnings: [],
    },
  ],
  warnings: ["One or more holdings have no cost basis."],
  errors: ["Row 3 requires correction."],
  summary: {
    rowCount: 3,
    validRowCount: 2,
    errorRowCount: 1,
    estimatedInvestedValue: "5412.05",
    estimatedCashValue: "14000",
  },
};

const confirmation = {
  batchId: preview.batchId,
  status: "CONFIRMED",
  version: 2,
  analysisRunId: "22222222-2222-2222-2222-222222222222",
  analysisState: "ANALYSIS_QUEUED",
  openPositionCount: 1,
  closedPositionCount: 0,
  cashRowCount: 1,
  compensationRowCount: 0,
  idempotentReplay: false,
};

function json(value: unknown) {
  return Promise.resolve(
    new Response(JSON.stringify(value), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    }),
  );
}

describe("PortfolioImportPageTest", () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    fetchMock.mockReset();
    fetchMock.mockImplementation((input: RequestInfo | URL) => {
      const path =
        typeof input === "string"
          ? input
          : input instanceof URL
            ? input.href
            : input.url;
      if (path.endsWith("/auth/csrf"))
        return json({ headerName: "X-CSRF-TOKEN", token: "secure-token" });
      if (path.endsWith("/fidelity/preview")) return json(preview);
      if (path.endsWith("/confirm")) return json(confirmation);
      throw new Error(`Unexpected fetch ${path}`);
    });
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("previews a real file and requires every unknown row to be handled before confirmation", async () => {
    const user = userEvent.setup();
    render(<PortfolioImportPage />);

    expect(
      screen.getByText(/does not connect to or operate your Fidelity account/i),
    ).toBeInTheDocument();
    await user.upload(
      screen.getByLabelText(/select fidelity positions csv/i),
      new File(["positions"], "positions.csv", { type: "text/csv" }),
    );

    expect(
      await screen.findByText("5412.05", { exact: false }),
    ).toBeInTheDocument();
    const confirm = screen.getByRole("button", {
      name: /confirm and queue analysis/i,
    });
    expect(confirm).toBeDisabled();
    await user.click(screen.getByRole("checkbox", { name: /ignore/i }));
    expect(confirm).toBeEnabled();
    await user.click(confirm);

    expect(
      await screen.findByRole("heading", { name: /analysis has been queued/i }),
    ).toBeInTheDocument();
    expect(screen.getByText("ANALYSIS_QUEUED")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(4);
  });

  it("allows an unknown holding to be corrected instead of ignored", async () => {
    const user = userEvent.setup();
    render(<PortfolioImportPage />);
    await user.upload(
      screen.getByLabelText(/select fidelity positions csv/i),
      new File(["positions"], "positions.csv", { type: "text/csv" }),
    );

    const confirm = await screen.findByRole("button", {
      name: /confirm and queue analysis/i,
    });
    await user.type(screen.getByLabelText("Symbol row 3"), "DXYZ");
    await user.selectOptions(
      screen.getByLabelText("Asset type row 3"),
      "EQUITY",
    );
    expect(confirm).toBeEnabled();
  });
});
