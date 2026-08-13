import { useState } from "react";
import { presentClassification } from "../presentation/classification-presentation";
import type { ImportPreview, RowOverride } from "./types";

export function RoleConfirmationStep({
  preview,
  overrides,
  reviewedRows,
  onOverride,
  onReviewed,
  onBack,
  onNext,
}: {
  preview: ImportPreview;
  overrides: Record<number, RowOverride>;
  reviewedRows: Set<number>;
  onOverride: (value: RowOverride) => void;
  onReviewed: (rowNumber: number, reviewed: boolean) => void;
  onBack: () => void;
  onNext: () => void;
}) {
  const [activeHolding, setActiveHolding] = useState(0);
  const holdings = preview.holdings.filter(
    (row) =>
      !overrides[row.rowNumber]?.ignored &&
      (overrides[row.rowNumber]?.rowType ?? row.rowType) === "HOLDING",
  );
  const ready = holdings.every((row) => {
    const classification = overrides[row.rowNumber]?.classification;
    return Boolean(
      classification &&
      classification !== "UNKNOWN" &&
      reviewedRows.has(row.rowNumber),
    );
  });
  return (
    <section className="import-role-step">
      <div className="section-title">
        <h2>确认每个持仓的组合角色</h2>
        <span>{holdings.length}</span>
      </div>
      <p>
        角色来自导入识别结果；你可以更改。角色会决定后续策略限制，但不会触发交易。
      </p>
      <p className="import-role-mobile-progress" aria-live="polite">
        正在确认第 {Math.min(activeHolding + 1, holdings.length)} 个，共{" "}
        {holdings.length} 个
      </p>
      <div className="import-role-list">
        {holdings.map((row, index) => {
          const override = overrides[row.rowNumber];
          const classification = override?.classification ?? "UNKNOWN";
          return (
            <article
              className="context-card import-role-card"
              data-mobile-active={index === activeHolding ? "true" : "false"}
              key={row.rowNumber}
            >
              <header>
                <div>
                  <strong>{row.symbol ?? "代码待修正"}</strong>
                  <small>{row.description}</small>
                </div>
                <span>{presentClassification(classification)}</span>
              </header>
              <p>
                <b>系统依据：</b>
                {row.classificationReason ??
                  "当前没有足够依据，必须由你选择角色。"}
              </p>
              <label>
                组合角色
                <select
                  aria-label={`Classification row ${String(row.rowNumber)}`}
                  value={classification}
                  onChange={(event) => {
                    onOverride({
                      ...override,
                      rowNumber: row.rowNumber,
                      ignored: false,
                      classification: event.target.value,
                    });
                    onReviewed(row.rowNumber, false);
                  }}
                >
                  <option value="UNKNOWN">请选择角色</option>
                  <option value="CORE_BROAD_ETF">宽基核心 ETF</option>
                  <option value="CORE_TECH_ETF">科技核心 ETF</option>
                  <option value="QUALITY_STOCK">优质公司核心仓</option>
                  <option value="QUALITY_GROWTH_HIGH_VOL">
                    高波动优质成长仓
                  </option>
                  <option value="THEMATIC_ETF">主题 ETF 主动仓</option>
                  <option value="TACTICAL_STOCK">主动股票仓</option>
                  <option value="CYCLICAL_TACTICAL">周期主动仓</option>
                  <option value="TURNAROUND_TACTICAL">困境反转主动仓</option>
                  <option value="SPECULATIVE">投机仓</option>
                </select>
              </label>
              <label className="role-confirmation-check">
                <input
                  type="checkbox"
                  checked={reviewedRows.has(row.rowNumber)}
                  disabled={classification === "UNKNOWN"}
                  onChange={(event) => {
                    onReviewed(row.rowNumber, event.target.checked);
                  }}
                />
                我确认这个角色适合该持仓
              </label>
            </article>
          );
        })}
      </div>
      <div className="import-role-mobile-nav" aria-label="逐项确认持仓">
        <button
          type="button"
          disabled={activeHolding === 0}
          onClick={() => {
            setActiveHolding((current) => Math.max(0, current - 1));
          }}
        >
          上一个持仓
        </button>
        <button
          type="button"
          disabled={activeHolding >= holdings.length - 1}
          onClick={() => {
            setActiveHolding((current) =>
              Math.min(holdings.length - 1, current + 1),
            );
          }}
        >
          下一个持仓
        </button>
      </div>
      <div className="import-actions">
        <button type="button" onClick={onBack}>
          上一步
        </button>
        <button type="button" disabled={!ready} onClick={onNext}>
          继续设置备用金
        </button>
      </div>
    </section>
  );
}
