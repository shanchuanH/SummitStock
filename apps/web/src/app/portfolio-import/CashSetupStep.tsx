import type { CashSetup } from "./types";

type Props = {
  value: CashSetup | undefined;
  onChange: (value: CashSetup) => void;
};

export function CashSetupStep({ value, onChange }: Props) {
  return (
    <section className="context-card import-cash-card">
      <p className="eyebrow">STEP 4 / SAFETY CASH</p>
      <h2>Where is your $20,000 living-expense safety cash?</h2>
      <p>
        This money is protected from investment sizing and is never treated as
        available buying power.
      </p>
      <fieldset>
        <legend>Safety-cash location</legend>
        {[
          ["IN_FIDELITY", "It is in the Fidelity cash shown above"],
          ["EXTERNAL_BANK", "It is in an external bank"],
          ["SPLIT", "It is split between Fidelity and a bank"],
          ["BELOW_TARGET", "I have not reached $20,000 yet"],
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
            ? "Amount held outside Fidelity"
            : "Current safety-cash amount"}
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
