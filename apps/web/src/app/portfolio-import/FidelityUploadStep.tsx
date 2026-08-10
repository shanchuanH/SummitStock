import { useState, type SyntheticEvent } from "react";

export type ImportMethod = "file" | "pasted" | "manual";

type Props = {
  busy: boolean;
  onFile: (file: File) => void;
  onPasted: (table: string) => void;
  onManual: (holding: Record<string, string>) => void;
};

export function FidelityUploadStep({
  busy,
  onFile,
  onPasted,
  onManual,
}: Props) {
  const [method, setMethod] = useState<ImportMethod>("file");
  const [table, setTable] = useState("");

  function submitManual(event: SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    onManual(
      Object.fromEntries(
        [...data.entries()].map(([key, value]) => [
          key,
          typeof value === "string" ? value : value.name,
        ]),
      ),
    );
  }

  return (
    <section className="context-card import-source-card">
      <p className="eyebrow">STEP 1 / SOURCE</p>
      <h2>Import real holdings</h2>
      <p className="import-safety">
        SummitStock does not connect to or operate your Fidelity account. The
        selected file or pasted data is used only to build an analysis preview.
      </p>
      <div className="import-tabs" role="tablist" aria-label="Import method">
        {(["file", "pasted", "manual"] as const).map((value) => (
          <button
            aria-selected={method === value}
            key={value}
            onClick={() => {
              setMethod(value);
            }}
            role="tab"
            type="button"
          >
            {value === "file"
              ? "Fidelity CSV"
              : value === "pasted"
                ? "Pasted table"
                : "Manual holding"}
          </button>
        ))}
      </div>
      {method === "file" ? (
        <label className="file-drop">
          <strong>Select Fidelity Positions CSV</strong>
          <span>Maximum 5 MB. The preview does not change your portfolio.</span>
          <input
            accept=".csv,text/csv"
            disabled={busy}
            onChange={(event) => {
              const file = event.target.files?.[0];
              if (file) onFile(file);
            }}
            type="file"
          />
        </label>
      ) : method === "pasted" ? (
        <div className="import-form">
          <label>
            Fidelity positions table
            <textarea
              aria-label="Fidelity positions table"
              onChange={(event) => {
                setTable(event.target.value);
              }}
              placeholder="Paste the header row and position rows copied from Fidelity"
              rows={8}
              value={table}
            />
          </label>
          <button
            disabled={busy || !table.trim()}
            onClick={() => {
              onPasted(table);
            }}
            type="button"
          >
            Preview pasted table
          </button>
        </div>
      ) : (
        <form className="import-form manual-grid" onSubmit={submitManual}>
          <label>
            Account name
            <input name="accountName" required />
          </label>
          <label>
            Masked account number
            <input name="accountNumber" placeholder="***1234" />
          </label>
          <label>
            Symbol
            <input name="symbol" required />
          </label>
          <label>
            Asset type
            <select defaultValue="EQUITY" name="assetType">
              <option>EQUITY</option>
              <option>ETF</option>
              <option>MUTUAL FUND</option>
              <option>Money Market</option>
              <option>Restricted Stock</option>
            </select>
          </label>
          <label>
            Description
            <input name="description" />
          </label>
          <label>
            Quantity
            <input inputMode="decimal" name="quantity" required />
          </label>
          <label>
            Last price
            <input inputMode="decimal" name="lastPrice" />
          </label>
          <label>
            Current value
            <input inputMode="decimal" name="currentValue" required />
          </label>
          <label>
            Average cost
            <input inputMode="decimal" name="averageCost" />
          </label>
          <label>
            Total cost basis
            <input inputMode="decimal" name="costBasis" />
          </label>
          <button disabled={busy} type="submit">
            Preview holding
          </button>
        </form>
      )}
    </section>
  );
}
