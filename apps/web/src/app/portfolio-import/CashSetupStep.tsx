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

const locations: Array<[CashSetup["location"], string]> = [
  ["IN_FIDELITY", "全部在 Fidelity 现金中"],
  ["EXTERNAL_BANK", "全部在外部银行中"],
  ["SPLIT", "Fidelity 和外部银行中都有"],
  ["BELOW_TARGET", "目前还没有达到策略目标"],
];

export function CashSetupStep({
  value,
  importedCash,
  emergencyTarget,
  onChange,
  onBack,
  onNext,
}: Props) {
  const fidelityCash = Number(importedCash);
  const target = Number(emergencyTarget);
  const fidelityAmount = parseAmount(value?.fidelityAmount);
  const externalAmount = parseAmount(value?.externalAmount);
  const total = fidelityAmount + externalAmount;
  const fidelityValid =
    value?.fidelityAmount.trim() !== "" &&
    Number.isFinite(fidelityAmount) &&
    fidelityAmount >= 0 &&
    fidelityAmount <= fidelityCash;
  const externalValid =
    value?.externalAmount.trim() !== "" &&
    Number.isFinite(externalAmount) &&
    externalAmount >= 0;
  const locationValid =
    value?.location === "IN_FIDELITY"
      ? externalAmount === 0
      : value?.location === "EXTERNAL_BANK"
        ? fidelityAmount === 0
        : value?.location === "SPLIT"
          ? fidelityAmount > 0 && externalAmount > 0
          : value?.location === "BELOW_TARGET"
            ? total < target
            : false;
  const ready = fidelityValid && externalValid && locationValid;

  function selectLocation(location: CashSetup["location"]) {
    onChange({
      location,
      fidelityAmount:
        location === "EXTERNAL_BANK" ? "0" : (value?.fidelityAmount ?? ""),
      externalAmount:
        location === "IN_FIDELITY" ? "0" : (value?.externalAmount ?? ""),
    });
  }

  return (
    <section className="context-card import-cash-card">
      <p className="eyebrow">第 4 步 / 生活备用金</p>
      <h2>确认备用金实际存放位置</h2>
      <p>
        策略目标是 {formatMoney(emergencyTarget)}。这里记录真实现金事实，不会用策略目标猜测你的银行或
        Fidelity 余额。
      </p>
      <fieldset>
        <legend>你的备用金目前在哪里？</legend>
        {locations.map(([location, label]) => (
          <label key={location}>
            <input
              checked={value?.location === location}
              name="cash-location"
              onChange={() => {
                selectLocation(location);
              }}
              type="radio"
            />
            {label}
          </label>
        ))}
      </fieldset>

      {value && value.location !== "EXTERNAL_BANK" ? (
        <label className="cash-confirmed-amount">
          Fidelity 中保护的备用金
          <input
            aria-label="Fidelity 中保护的备用金"
            min="0"
            onChange={(event) => {
              onChange({ ...value, fidelityAmount: event.target.value });
            }}
            step="0.01"
            type="number"
            value={value.fidelityAmount}
          />
        </label>
      ) : null}

      {value && value.location !== "IN_FIDELITY" ? (
        <label className="cash-confirmed-amount">
          外部银行中的备用金
          <input
            aria-label="外部银行中的备用金"
            min="0"
            onChange={(event) => {
              onChange({ ...value, externalAmount: event.target.value });
            }}
            step="0.01"
            type="number"
            value={value.externalAmount}
          />
        </label>
      ) : null}

      {value && fidelityValid && externalValid ? (
        <div className="cash-impact-summary">
          <p>
            备用金合计：<strong>{formatMoney(total)}</strong>
          </p>
          <p>
            策略目标：<strong>{formatMoney(target)}</strong>
          </p>
          <p>
            距离目标：
            <strong>{formatMoney(Math.max(0, target - total))}</strong>
          </p>
          <p>
            Fidelity 剩余可投资现金：
            <strong>{formatMoney(Math.max(0, fidelityCash - fidelityAmount))}</strong>
          </p>
        </div>
      ) : null}

      {value && fidelityAmount > fidelityCash ? (
        <p className="import-warning" role="alert">
          Fidelity 中保护的金额不能超过本次文件识别到的 Fidelity 现金。
        </p>
      ) : null}
      {value?.location === "SPLIT" &&
      fidelityValid &&
      externalValid &&
      (fidelityAmount <= 0 || externalAmount <= 0) ? (
        <p className="import-warning" role="alert">
          选择两边都有时，请分别输入大于 0 的 Fidelity 和外部银行金额。
        </p>
      ) : null}
      {value?.location === "BELOW_TARGET" && total >= target ? (
        <p className="import-warning" role="alert">
          当前合计已达到策略目标，请选择实际对应的存放位置。
        </p>
      ) : null}
      {value && externalAmount > 0 ? (
        <p className="import-warning">
          SummitStock 无法验证外部银行余额；该金额来自你的明确确认。
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

function parseAmount(value: string | undefined) {
  return value === undefined || value.trim() === "" ? Number.NaN : Number(value);
}
