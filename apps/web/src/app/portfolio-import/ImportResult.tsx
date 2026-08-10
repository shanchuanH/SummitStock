import type { ImportConfirmation } from "./types";

export function ImportResult({
  result,
  onAnother,
}: {
  result: ImportConfirmation;
  onAnother: () => void;
}) {
  return (
    <section className="context-card import-result" aria-live="polite">
      <p className="eyebrow">IMPORT CONFIRMED</p>
      <h2>Analysis has been queued</h2>
      <p>
        {result.openPositionCount} open positions reconciled,{" "}
        {result.closedPositionCount} positions closed, {result.cashRowCount}{" "}
        cash accounts tracked, and {result.compensationRowCount} unvested
        compensation holdings kept outside liquid assets.
      </p>
      <dl>
        <div>
          <dt>Analysis state</dt>
          <dd>{result.analysisState}</dd>
        </div>
        <div>
          <dt>Run</dt>
          <dd>{result.analysisRunId}</dd>
        </div>
      </dl>
      <div className="import-actions">
        <a href="/">Return to dashboard</a>
        <button onClick={onAnother} type="button">
          Import another snapshot
        </button>
      </div>
    </section>
  );
}
