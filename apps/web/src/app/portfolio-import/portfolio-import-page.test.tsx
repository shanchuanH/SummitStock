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
      suggestedClassification: "CORE_BROAD_ETF",
      classificationReason: "Recognized broad-market ETF.",
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
    emergencyCashTarget: "20000",
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
  cashflowReconciliation: { status: "NONE", cashChange: "0" },
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
      if (path.includes("/analysis/status/"))
        return json({
          runId: confirmation.analysisRunId,
          state: "RUNNING",
          worker: { alive: true, lastSeenAt: "2026-08-12T20:00:00Z" },
          progress: {
            completed: 1,
            total: 27,
            currentStage: "COLLECT_QUOTES",
            lastProgressAt: "2026-08-12T20:00:00Z",
          },
          stages: [
            {
              code: "HOLDINGS",
              label: "Holdings imported",
              status: "COMPLETE",
            },
            { code: "PRICES", label: "Prices", status: "RUNNING" },
          ],
        });
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
      screen.getByText(/不会登录 Fidelity，也不会替你交易/i),
    ).toBeInTheDocument();
    await user.upload(
      screen.getByLabelText(/选择 Fidelity Positions CSV/i),
      new File(["positions"], "positions.csv", { type: "text/csv" }),
    );

    expect(
      await screen.findByText("5412.05", { exact: false }),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("checkbox", { name: /ignore/i }));
    await user.click(screen.getByRole("button", { name: /继续确认角色/i }));
    await user.click(screen.getByRole("checkbox", { name: /我确认这个角色/i }));
    await user.click(screen.getByRole("button", { name: /继续设置备用金/i }));
    await user.click(screen.getByRole("radio", { name: /^全部在外部银行中$/i }));
    expect(screen.getByLabelText("外部银行中的备用金")).toHaveValue(null);
    await user.type(screen.getByLabelText("外部银行中的备用金"), "14000");
    await user.click(screen.getByRole("button", { name: /继续最终确认/i }));
    const confirm = screen.getByRole("button", { name: /确认并开始分析/i });
    await user.click(confirm);

    expect(
      await screen.findByRole("heading", { name: /分析正在执行/i }),
    ).toBeInTheDocument();
    expect(screen.getByText("在线")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(5);
  });

  it("allows an unknown holding to be corrected instead of ignored", async () => {
    const user = userEvent.setup();
    render(<PortfolioImportPage />);
    await user.upload(
      screen.getByLabelText(/选择 Fidelity Positions CSV/i),
      new File(["positions"], "positions.csv", { type: "text/csv" }),
    );

    await screen.findByText("5412.05", { exact: false });
    await user.type(screen.getByLabelText("Symbol row 3"), "DXYZ");
    await user.selectOptions(
      screen.getByLabelText("Asset type row 3"),
      "EQUITY",
    );
    await user.selectOptions(
      screen.getByLabelText("Classification row 3"),
      "SPECULATIVE",
    );
    await user.click(screen.getByRole("button", { name: /继续确认角色/i }));
    const confirmations = screen.getAllByRole("checkbox", {
      name: /我确认这个角色/i,
    });
    expect(confirmations).toHaveLength(2);
    for (const confirmationBox of confirmations) {
      await user.click(confirmationBox);
    }
    expect(
      screen.getByRole("button", { name: /继续设置备用金/i }),
    ).toBeEnabled();
  });
});
