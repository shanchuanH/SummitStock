import type { components } from "@portfolio/api-client";

type PortfolioHealth = components["schemas"]["PortfolioHealth"];

export function PortfolioHealthCard({ health }: { health: PortfolioHealth }) {
  return (
    <article className="context-card">
      <p className="eyebrow">PORTFOLIO HEALTH</p>
      <h2>{health.status}</h2>
      {health.reasons.length ? (
        <ul>
          {health.reasons.map((reason) => (
            <li key={reason}>{reason}</li>
          ))}
        </ul>
      ) : (
        <p>No active health warnings.</p>
      )}
    </article>
  );
}
