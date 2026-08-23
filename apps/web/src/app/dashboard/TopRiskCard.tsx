import type { components } from "@portfolio/api-client";

type TopRisk = components["schemas"]["TopRisk"];

export function TopRiskCard({ risk }: { risk: TopRisk }) {
  return (
    <li className="context-card top-risk-card">
      <strong>
        {risk.symbol ? `${risk.symbol} · ` : ""}
        {risk.risk}
      </strong>
      <p>
        <b>这意味着：</b>
        {risk.meaning}
      </p>
      <p>
        <b>现在应该做：</b>
        {risk.nowAction}
      </p>
    </li>
  );
}
