import type { CashSetup } from "./types";

type Props = {
  value: CashSetup | undefined;
  onChange: (value: CashSetup) => void;
};

export function CashSetupStep({ value, onChange }: Props) {
  return (
    <section className="context-card import-cash-card">
      <p className="eyebrow">第 4 步 / 生活备用金</p>
      <h2>你的 $20,000 生活备用金放在哪里？</h2>
      <p>这笔钱不会参与投资仓位计算，也不会被当作可买入资金。</p>
      <fieldset>
        <legend>备用金位置</legend>
        {[
          ["IN_FIDELITY", "在上面显示的 Fidelity 现金中"],
          ["EXTERNAL_BANK", "在外部银行"],
          ["SPLIT", "Fidelity 和外部银行都有"],
          ["BELOW_TARGET", "目前还没有达到 $20,000"],
        ].map(([location, label]) => (
          <label key={location}>
            <input
              checked={value?.location === location}
              name="cash-location"
              onChange={() => {
                onChange({ location: location as CashSetup["location"] });
              }}
              type="radio"
            />
            {label}
          </label>
        ))}
      </fieldset>
      {value?.location === "SPLIT" || value?.location === "BELOW_TARGET" ? (
        <label>
          {value.location === "SPLIT"
            ? "Fidelity 之外的备用金金额"
            : "当前备用金总额"}
          <input
            aria-label="External safety cash amount"
            min="0"
            onChange={(event) => {
              onChange({
                ...value,
                externalEmergencyAmount: event.target.value,
              });
            }}
            placeholder="0"
            step="0.01"
            type="number"
            value={value.externalEmergencyAmount ?? ""}
          />
        </label>
      ) : null}
    </section>
  );
}
