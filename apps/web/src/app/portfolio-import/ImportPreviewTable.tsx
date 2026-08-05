import type { ImportPreview, RowOverride } from "./types";

type Props = {
  preview: ImportPreview;
  overrides: Record<number, RowOverride>;
  onOverride: (override: RowOverride) => void;
};

export function ImportPreviewTable({ preview, overrides, onOverride }: Props) {
  return (
    <section className="context-card import-preview-card">
      <p className="eyebrow">STEP 2 / PREVIEW</p>
      <div className="import-summary">
        <span>{preview.summary.rowCount} source rows</span>
        <span>${preview.summary.estimatedInvestedValue} invested</span>
        <span>${preview.summary.estimatedCashValue} cash</span>
        <span>{preview.summary.errorRowCount} errors</span>
      </div>
      {preview.warnings.map((warning) => (
        <p className="import-warning" key={warning}>
          {warning}
        </p>
      ))}
      <div className="import-table-wrap">
        <table className="import-table">
          <thead>
            <tr>
              <th>Row</th>
              <th>Account</th>
              <th>Symbol</th>
              <th>Type</th>
              <th>Quantity</th>
              <th>Value</th>
              <th>Cost basis</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {preview.holdings.map((row) => {
              const override = overrides[row.rowNumber];
              return (
                <tr
                  className={`import-row--${row.status.toLowerCase()}`}
                  key={row.rowNumber}
                >
                  <td>{row.rowNumber}</td>
                  <td>
                    {row.accountName ?? "Unknown"}
                    <small>{row.accountNumberMasked}</small>
                  </td>
                  <td>
                    {row.status === "ERROR" && !override?.ignored ? (
                      <input
                        aria-label={`Symbol row ${String(row.rowNumber)}`}
                        onChange={(event) => {
                          onOverride({
                            ...override,
                            rowNumber: row.rowNumber,
                            ignored: false,
                            symbol: event.target.value,
                            rowType: "HOLDING",
                          });
                        }}
                        value={override?.symbol ?? row.symbol ?? ""}
                      />
                    ) : (
                      (row.symbol ?? "—")
                    )}
                  </td>
                  <td>
                    {row.status === "ERROR" && !override?.ignored ? (
                      <select
                        aria-label={`Asset type row ${String(row.rowNumber)}`}
                        onChange={(event) => {
                          onOverride({
                            ...override,
                            rowNumber: row.rowNumber,
                            ignored: false,
                            assetType: event.target.value,
                            rowType: "HOLDING",
                          });
                        }}
                        value={override?.assetType ?? "UNKNOWN"}
                      >
                        <option value="UNKNOWN">Select type</option>
                        <option value="EQUITY">Equity</option>
                        <option value="ETF">ETF</option>
                        <option value="MUTUAL_FUND">Mutual fund</option>
                      </select>
                    ) : (
                      row.assetType
                    )}
                  </td>
                  <td>{row.quantity ?? "—"}</td>
                  <td>{row.currentValue ?? "—"}</td>
                  <td>{row.costBasis ?? "Missing"}</td>
                  <td>
                    <strong>
                      {override?.ignored ? "IGNORED" : row.status}
                    </strong>
                    {row.status === "ERROR" ? (
                      <label className="ignore-row">
                        <input
                          checked={override?.ignored ?? false}
                          onChange={(event) => {
                            onOverride({
                              rowNumber: row.rowNumber,
                              ignored: event.target.checked,
                            });
                          }}
                          type="checkbox"
                        />{" "}
                        Ignore
                      </label>
                    ) : null}
                  </td>
                </tr>
              );
            })}
            {preview.cash.map((row) => (
              <tr key={row.rowNumber}>
                <td>{row.rowNumber}</td>
                <td>
                  {row.accountName}
                  <small>{row.accountNumberMasked}</small>
                </td>
                <td>{row.symbol}</td>
                <td>Cash-like</td>
                <td>—</td>
                <td>{row.currentValue}</td>
                <td>—</td>
                <td>
                  <strong>{row.status}</strong>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
