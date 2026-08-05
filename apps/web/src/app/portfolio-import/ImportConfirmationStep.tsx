import type { ImportPreview } from "./types";

export function ImportConfirmationStep({
  preview,
  ready,
  busy,
  onConfirm,
  onReset,
}: {
  preview: ImportPreview;
  ready: boolean;
  busy: boolean;
  onConfirm: () => void;
  onReset: () => void;
}) {
  return (
    <section className="context-card import-confirm-card">
      <p className="eyebrow">STEP 3 / CONFIRM</p>
      <h2>Reconcile this full account snapshot</h2>
      <p>
        Existing matching positions will be updated, historical snapshots will
        be retained, and missing holdings in these imported accounts will be
        closed.
      </p>
      {!ready ? (
        <p className="import-warning" role="alert">
          Correct or explicitly ignore every error row before confirming.
        </p>
      ) : null}
      <div className="import-actions">
        <button disabled={!ready || busy} onClick={onConfirm} type="button">
          {busy ? "Confirming…" : "Confirm and queue analysis"}
        </button>
        <button disabled={busy} onClick={onReset} type="button">
          Start over
        </button>
      </div>
      <small>
        Batch {preview.batchId} · version {preview.version}
      </small>
    </section>
  );
}
