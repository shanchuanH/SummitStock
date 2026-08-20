import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ImportResult } from "./ImportResult";

const result = {
  batchId: "11111111-1111-1111-1111-111111111111",
  status: "CONFIRMED",
  version: 1,
  analysisRunId: "22222222-2222-2222-2222-222222222222",
  analysisState: "ANALYSIS_QUEUED",
  openPositionCount: 1,
  closedPositionCount: 0,
  cashRowCount: 1,
  compensationRowCount: 0,
  idempotentReplay: false,
  cashflowReconciliation: { status: "NONE", cashChange: "0" },
};

describe("ImportResult", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("states that the worker is offline instead of rendering fake waiting progress", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            runId: result.analysisRunId,
            state: "WORKER_OFFLINE",
            worker: { alive: false },
            progress: {
              completed: 0,
              total: 27,
              lastProgressAt: "2026-08-20T12:00:00Z",
            },
            pendingAgeSeconds: 15,
            stages: [],
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
      ),
    );
    render(<ImportResult result={result} onAnother={vi.fn()} />);
    expect(
      await screen.findByRole("heading", { name: "分析服务没有运行" }),
    ).toBeInTheDocument();
    expect(screen.getByText("离线")).toBeInTheDocument();
    expect(screen.queryByText("WAITING")).not.toBeInTheDocument();
  });

  it("asks the owner to classify an unexplained broker cash movement", () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("offline")));
    render(
      <ImportResult
        result={{
          ...result,
          cashflowReconciliation: {
            reconciliationId: "33333333-3333-3333-3333-333333333333",
            status: "REQUIRED",
            cashChange: "7000",
          },
        }}
        onAnother={vi.fn()}
      />,
    );

    expect(screen.getByRole("heading", { name: /现金增加.*7,000/ })).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "外部入金或提款" })).toBeChecked();
    expect(screen.getByRole("radio", { name: "买卖持仓产生的现金" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "确认现金变化" })).toBeInTheDocument();
  });
});
