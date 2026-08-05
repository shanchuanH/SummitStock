const links = [
  ["Dashboard", "/"],
  ["Regime", "/market-context"],
  ["Portfolio", "/portfolio"],
  ["Plan", "/plan"],
  ["Dip", "/dip-buy"],
  ["Thesis", "/thesis"],
  ["Journal", "/journal"],
  ["Settings", "/settings"],
  ["Data Health", "/data-health"],
  ["Backtest", "/backtests"],
] as const;

export function WorkspaceNav() {
  return (
    <nav className="workspace-nav" aria-label="Portfolio workspace">
      {links.map(([label, href]) => (
        <a key={href} href={href}>
          {label}
        </a>
      ))}
    </nav>
  );
}
