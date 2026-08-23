import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
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
    expect(screen.getByText(/启动完整环境：make dev/)).toBeInTheDocument();
    expect(screen.queryByText("WAITING")).not.toBeInTheDocument();
  });

  it("shows stalled stage, update time, and recovery actions", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            runId: result.analysisRunId,
            state: "STALLED",
            worker: { alive: true },
            progress: {
              completed: 8,
              total: 27,
              currentStage: "公司财务",
              lastProgressAt: "2026-08-20T12:00:00Z",
            },
            stages: [],
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
      ),
    );

    render(<ImportResult result={result} onAnother={vi.fn()} />);

    expect(
      await screen.findByRole("heading", { name: "分析长时间没有推进" }),
    ).toBeInTheDocument();
    expect(screen.getByText("公司财务")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "重新分析" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "检查数据源" })).toHaveAttribute(
      "href",
      "/advanced/data-health",
    );
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

    expect(
      screen.getByRole("heading", { name: /现金增加.*7,000/ }),
    ).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "外部入金或提款" })).toBeChecked();
    expect(
      screen.getByRole("radio", { name: "买卖持仓产生的现金" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "确认现金变化" }),
    ).toBeInTheDocument();
  });

  it("polls the replacement run id returned by stalled recovery", async () => {
    const replacement = "44444444-4444-4444-4444-444444444444";
    const fetchMock = vi
      .fn()
      .mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
        const path =
          typeof input === "string"
            ? input
            : input instanceof URL
              ? input.href
              : input.url;
        if (path.endsWith("/auth/csrf"))
          return Promise.resolve(
            new Response(
              JSON.stringify({ headerName: "X-CSRF-TOKEN", token: "secure" }),
              { status: 200 },
            ),
          );
        if (path.endsWith("/analysis/runs") && init?.method === "POST")
          return Promise.resolve(
            new Response(
              JSON.stringify({ runId: replacement, state: "STARTING" }),
              { status: 202 },
            ),
          );
        const runId = path.split("/").at(-1);
        return Promise.resolve(
          new Response(
            JSON.stringify({
              runId,
              state: runId === replacement ? "RUNNING" : "STALLED",
              worker: { alive: true },
              progress: {
                completed: 1,
                total: 27,
                lastProgressAt: "2026-08-20T12:00:00Z",
              },
              stages: [],
            }),
            { status: 200, headers: { "Content-Type": "application/json" } },
          ),
        );
      });
    vi.stubGlobal("fetch", fetchMock);
    render(<ImportResult result={result} onAnother={vi.fn()} />);

    await userEvent.click(
      await screen.findByRole("button", { name: /重新分析/ }),
    );

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        `/api/v1/analysis/status/${replacement}`,
        expect.objectContaining({ cache: "no-store" }),
      );
    });
  });
});
