import { useState } from "react";
import { WorkspaceNav } from "../workspace-nav";
import { FidelityUploadStep } from "./FidelityUploadStep";
import { ImportConfirmationStep } from "./ImportConfirmationStep";
import { ImportPreviewTable } from "./ImportPreviewTable";
import { ImportResult } from "./ImportResult";
import { CashSetupStep } from "./CashSetupStep";
import { RoleConfirmationStep } from "./RoleConfirmationStep";
import type {
  CashSetup,
  ImportConfirmation,
  ImportPreview,
  RowOverride,
} from "./types";

type CsrfToken = { headerName: string; token: string };

async function postImport<T>(
  path: string,
  body: FormData | object,
): Promise<T> {
  const tokenResponse = await fetch("/api/v1/auth/csrf", {
    cache: "no-store",
    credentials: "same-origin",
  });
  if (!tokenResponse.ok)
    throw new Error("Unable to establish a secure import session.");
  const csrf = (await tokenResponse.json()) as CsrfToken;
  const form = body instanceof FormData;
  const response = await fetch(path, {
    method: "POST",
    credentials: "same-origin",
    headers: {
      [csrf.headerName]: csrf.token,
      ...(form ? {} : { "Content-Type": "application/json" }),
    },
    body: form ? body : JSON.stringify(body),
  });
  if (!response.ok) {
    const detail = (await response.json().catch(() => null)) as {
      detail?: string;
      message?: string;
    } | null;
    throw new Error(
      detail?.detail ??
        detail?.message ??
        `Import request failed (${String(response.status)}).`,
    );
  }
  return response.json() as Promise<T>;
}

export function PortfolioImportPage() {
  const [step, setStep] = useState<1 | 2 | 3 | 4 | 5>(1);
  const [preview, setPreview] = useState<ImportPreview>();
  const [result, setResult] = useState<ImportConfirmation>();
  const [overrides, setOverrides] = useState<Record<number, RowOverride>>({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const [cashSetup, setCashSetup] = useState<CashSetup>();
  const [reviewedRows, setReviewedRows] = useState<Set<number>>(new Set());

  async function request(factory: () => Promise<ImportPreview>) {
    setBusy(true);
    setError(undefined);
    try {
      const value = await factory();
      setPreview(value);
      const initialOverrides: Record<number, RowOverride> = {};
      for (const row of value.holdings.filter(
        (holding) => holding.rowType === "HOLDING",
      )) {
        initialOverrides[row.rowNumber] = {
          rowNumber: row.rowNumber,
          rowType: row.rowType,
          ignored: false,
          ...(row.symbol === undefined ? {} : { symbol: row.symbol }),
          ...(row.assetType === undefined ? {} : { assetType: row.assetType }),
          ...(row.suggestedClassification === undefined
            ? {}
            : { classification: row.suggestedClassification }),
        };
      }
      setOverrides(initialOverrides);
      setCashSetup(undefined);
      setReviewedRows(new Set());
      setStep(2);
    } catch (value) {
      setError(
        value instanceof Error ? value.message : "Import preview failed.",
      );
    } finally {
      setBusy(false);
    }
  }

  function reset() {
    setPreview(undefined);
    setResult(undefined);
    setOverrides({});
    setCashSetup(undefined);
    setReviewedRows(new Set());
    setStep(1);
    setError(undefined);
  }
  const errorsReady =
    preview?.holdings
      .filter((row) => row.status === "ERROR")
      .every((row) => {
        const value = overrides[row.rowNumber];
        return (
          value?.ignored ||
          Boolean(
            value?.symbol &&
            value.assetType &&
            value.assetType !== "UNKNOWN" &&
            value.rowType,
          )
        );
      }) ?? false;
  const classificationsReady =
    preview?.holdings.every((row) => {
      const value = overrides[row.rowNumber];
      if (value?.ignored) return true;
      if ((value?.rowType ?? row.rowType) !== "HOLDING")
        return row.status !== "ERROR";
      return Boolean(
        value?.classification &&
        value.classification !== "UNKNOWN" &&
        reviewedRows.has(row.rowNumber),
      );
    }) ?? false;
  const cashReady =
    cashSetup !== undefined &&
    cashSetup.externalEmergencyAmount !== undefined &&
    Number.isFinite(Number(cashSetup.externalEmergencyAmount)) &&
    Number(cashSetup.externalEmergencyAmount) >= 0;

  async function confirm() {
    if (!preview) return;
    setBusy(true);
    setError(undefined);
    try {
      setResult(
        await postImport<ImportConfirmation>(
          `/api/v1/portfolio-imports/${preview.batchId}/confirm`,
          {
            expectedVersion: preview.version,
            accountMappings: [],
            rowOverrides: Object.values(overrides),
            cashSetup,
          },
        ),
      );
    } catch (value) {
      setError(
        value instanceof Error ? value.message : "Import confirmation failed.",
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="shell workspace-shell import-shell">
      <WorkspaceNav />
      <section className="workspace-heading">
        <p className="eyebrow">投资组合导入</p>
        <h1>导入持仓</h1>
        <p>先预览并修正识别结果，明确确认后才会写入投资组合。</p>
      </section>
      <ol className="import-stepper" aria-label="导入进度">
        {["上传", "检查", "确认角色", "备用金", "完成"].map((label, index) => {
          const number = (index + 1) as 1 | 2 | 3 | 4 | 5;
          return (
            <li
              aria-current={step === number ? "step" : undefined}
              className={step >= number ? "active" : ""}
              key={label}
            >
              <span>{number}</span>
              {label}
            </li>
          );
        })}
      </ol>
      {error ? (
        <aside className="error" role="alert">
          <strong>导入已停止</strong>
          <span>{error}</span>
        </aside>
      ) : null}
      {result ? (
        <ImportResult onAnother={reset} result={result} />
      ) : step === 1 || !preview ? (
        <FidelityUploadStep
          busy={busy}
          onFile={(file) => {
            const body = new FormData();
            body.append("file", file);
            void request(() =>
              postImport("/api/v1/portfolio-imports/fidelity/preview", body),
            );
          }}
          onPasted={(table) =>
            void request(() =>
              postImport("/api/v1/portfolio-imports/pasted/preview", { table }),
            )
          }
          onManual={(holding) =>
            void request(() =>
              postImport("/api/v1/portfolio-imports/manual/preview", holding),
            )
          }
        />
      ) : step === 2 ? (
        <div className="import-review-flow import-current-step">
          <ImportPreviewTable
            onOverride={(value) => {
              setOverrides((current) => ({
                ...current,
                [value.rowNumber]: value,
              }));
            }}
            overrides={overrides}
            preview={preview}
          />
          <div className="import-actions">
            <button type="button" onClick={reset}>
              上一步
            </button>
            <button
              type="button"
              disabled={!errorsReady}
              onClick={() => {
                setStep(3);
              }}
            >
              继续确认角色
            </button>
          </div>
        </div>
      ) : step === 3 ? (
        <RoleConfirmationStep
          preview={preview}
          overrides={overrides}
          reviewedRows={reviewedRows}
          onOverride={(value) => {
            setOverrides((current) => ({
              ...current,
              [value.rowNumber]: value,
            }));
          }}
          onReviewed={(rowNumber, reviewed) => {
            setReviewedRows((current) => {
              const next = new Set(current);
              if (reviewed) next.add(rowNumber);
              else next.delete(rowNumber);
              return next;
            });
          }}
          onBack={() => {
            setStep(2);
          }}
          onNext={() => {
            if (classificationsReady) setStep(4);
          }}
        />
      ) : step === 4 ? (
        <CashSetupStep
          value={cashSetup}
          importedCash={preview.summary.estimatedCashValue}
          emergencyTarget={preview.summary.emergencyCashTarget}
          onChange={setCashSetup}
          onBack={() => {
            setStep(3);
          }}
          onNext={() => {
            if (cashReady) setStep(5);
          }}
        />
      ) : cashSetup ? (
        <div className="import-current-step">
          <ImportConfirmationStep
            busy={busy}
            cashSetup={cashSetup}
            onConfirm={() => void confirm()}
            onBack={() => {
              setStep(4);
            }}
            onReset={reset}
            preview={preview}
          />
        </div>
      ) : null}
      <footer>
        <span>仅分析导入文件</span>
        <span>不连接券商</span>
        <span>不会自动交易</span>
      </footer>
    </main>
  );
}
