import { useState } from "react";
import { WorkspaceNav } from "../workspace-nav";
import { FidelityUploadStep } from "./FidelityUploadStep";
import { ImportConfirmationStep } from "./ImportConfirmationStep";
import { ImportPreviewTable } from "./ImportPreviewTable";
import { ImportResult } from "./ImportResult";
import type { ImportConfirmation, ImportPreview, RowOverride } from "./types";

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
  const [preview, setPreview] = useState<ImportPreview>();
  const [result, setResult] = useState<ImportConfirmation>();
  const [overrides, setOverrides] = useState<Record<number, RowOverride>>({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();

  async function request(factory: () => Promise<ImportPreview>) {
    setBusy(true);
    setError(undefined);
    try {
      setPreview(await factory());
      setOverrides({});
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
      {error ? (
        <aside className="error" role="alert">
          <strong>Import stopped</strong>
          <span>{error}</span>
        </aside>
      ) : null}
      {result ? (
        <ImportResult onAnother={reset} result={result} />
      ) : !preview ? (
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
      ) : (
        <>
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
          <ImportConfirmationStep
            busy={busy}
            onConfirm={() => void confirm()}
            onReset={reset}
            preview={preview}
            ready={errorsReady}
          />
        </>
      )}
      <footer>
        <span>仅分析导入文件</span>
        <span>不连接券商</span>
        <span>不会自动交易</span>
      </footer>
    </main>
  );
}
