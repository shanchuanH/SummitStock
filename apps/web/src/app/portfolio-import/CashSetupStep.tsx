import { formatMoney } from "../presentation/number-format";
import type { CashSetup } from "./types";

type Props = {
  value: CashSetup | undefined;
  importedCash: string;
  emergencyTarget: string;
  onChange: (value: CashSetup) => void;
  onBack: () => void;
  onNext: () => void;
};

export function CashSetupStep({
  value,
  importedCash,
  emergencyTarget,
  onChange,
  onBack,
  onNext,
}: Props) {
  const fidelityCash = Number(importedCash);
  const confirmed = Number(value?.externalEmergencyAmount ?? "");
  const fidelityProtected =
    value?.location === "IN_FIDELITY" ? Math.min(fidelityCash, confirmed) : 0;
  const ready =
    Boolean(value?.location) && Number.isFinite(confirmed) && confirmed >= 0;
  return (
    <section className="context-card import-cash-card">
      <p className="eyebrow">第 4 步 / 生活备用金</p>
      <h2>生活备用金</h2>
      <p>
        当前策略目标是 {formatMoney(emergencyTarget)}
        。这笔钱会始终受到保护，不会被算成可投资资金。
      </p>
      <fieldset>
        <legend>你的备用金目前在哪里？</legend>
        {[
          ["IN_FIDELITY", "全部在 Fidelity 现金里"],
          ["EXTERNAL_BANK", "全部在外部银行"],
          ["SPLIT", "两边都有"],
          ["BELOW_TARGET", `目前还不足 ${formatMoney(emergencyTarget)}`],
        ].map(([location, label]) => (
          <label key={location}>
            <input
              checked={value?.location === location}
              name="cash-location"
              onChange={() => {
                onChange({
                  location: location as CashSetup["location"],
                  externalEmergencyAmount:
                    value?.externalEmergencyAmount ?? emergencyTarget,
                });
              }}
              type="radio"
            />
            {label}
          </label>
        ))}
      </fieldset>
      {value ? (
        <label className="cash-confirmed-amount">
          {value.location === "EXTERNAL_BANK" || value.location === "SPLIT"
            ? "外部银行备用金金额"
            : value.location === "BELOW_TARGET"
              ? "目前已备好的金额"
              : "计划从 Fidelity 现金中保护的金额"}
          <input
            aria-label="确认生活备用金金额"
            min="0"
            onChange={(event) => {
              onChange({
                ...value,
                externalEmergencyAmount: event.target.value,
              });
            }}
            step="0.01"
            type="number"
            value={value.externalEmergencyAmount ?? ""}
          />
        </label>
      ) : null}
      {value?.location === "IN_FIDELITY" && ready ? (
        <div className="cash-impact-summary">
          <p>
            Fidelity 中可识别现金：<strong>{formatMoney(importedCash)}</strong>
          </p>
          <p>
            计划保护：<strong>{formatMoney(fidelityProtected)}</strong>
          </p>
          <p>
            仍缺：
            <strong>
              {formatMoney(Math.max(0, confirmed - fidelityProtected))}
            </strong>
          </p>
          <p>
            保护后可用于投资：
            <strong>
              {formatMoney(Math.max(0, fidelityCash - fidelityProtected))}
            </strong>
          </p>
        </div>
      ) : null}
      {value?.location === "EXTERNAL_BANK" || value?.location === "SPLIT" ? (
        <p className="import-warning">
          SummitStock 无法验证外部银行余额；这个数字来自你的手动确认。
        </p>
      ) : null}
      <div className="import-actions">
        <button type="button" onClick={onBack}>
          上一步
        </button>
        <button type="button" disabled={!ready} onClick={onNext}>
          继续最终确认
        </button>
      </div>
    </section>
  );
}
