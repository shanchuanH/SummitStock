import { formatMoney } from "../presentation/number-format";
import type { CashSetup, ImportPreview } from "./types";

export function ImportConfirmationStep({
  preview,
  cashSetup,
  busy,
  onConfirm,
  onBack,
  onReset,
}: {
  preview: ImportPreview;
  cashSetup: CashSetup;
  busy: boolean;
  onConfirm: () => void;
  onBack: () => void;
  onReset: () => void;
}) {
  const tradable = preview.holdings.filter(
    (row) => row.rowType === "HOLDING",
  ).length;
  const compensation = preview.holdings.filter(
    (row) => row.rowType === "COMPENSATION",
  ).length;
  return (
    <section className="context-card import-confirm-card">
      <p className="eyebrow">第 5 步 / 最终确认</p>
      <h2>即将导入</h2>
      <dl className="import-final-summary">
        <div>
          <dt>可交易持仓</dt>
          <dd>{tradable}</dd>
        </div>
        <div>
          <dt>不可交易公司股票</dt>
          <dd>{compensation}</dd>
        </div>
        <div>
          <dt>Fidelity 现金</dt>
          <dd>{formatMoney(preview.summary.estimatedCashValue)}</dd>
        </div>
        <div>
          <dt>生活备用金确认额</dt>
          <dd>{formatMoney(cashSetup.externalEmergencyAmount)}</dd>
        </div>
      </dl>
      <p>
        导入后 SummitStock 会自动分析这些持仓。不会登录 Fidelity，也不会向
        Fidelity 下单。
      </p>
      <div className="import-actions">
        <button disabled={busy} onClick={onBack} type="button">
          上一步
        </button>
        <button disabled={busy} onClick={onConfirm} type="button">
          {busy ? "正在确认…" : "确认并开始分析"}
        </button>
        <button disabled={busy} onClick={onReset} type="button">
          重新开始
        </button>
      </div>
      <details>
        <summary>查看导入批次</summary>
        <small>
          Batch {preview.batchId} · version {preview.version}
        </small>
      </details>
    </section>
  );
}
