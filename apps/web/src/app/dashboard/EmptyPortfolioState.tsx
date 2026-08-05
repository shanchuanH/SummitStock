export function EmptyPortfolioState() {
  return (
    <article className="context-card dashboard-actions">
      <p className="eyebrow">PORTFOLIO REQUIRED</p>
      <h2>No portfolio has been imported</h2>
      <p>
        No analysis or action conclusion is available until real holdings are
        imported and confirmed.
      </p>
      <a className="market-link" href="/portfolio-import">
        Import Fidelity holdings →
      </a>
    </article>
  );
}
