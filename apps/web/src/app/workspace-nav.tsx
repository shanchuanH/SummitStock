const links = [
  ["今日简报", "/"],
  ["我的持仓", "/portfolio"],
  ["市场与机会", "/opportunities"],
  ["复盘", "/review"],
  ["设置", "/settings"],
] as const;

export function WorkspaceNav() {
  return (
    <nav className="workspace-nav" aria-label="投资组合工作区">
      {links.map(([label, href]) => (
        <a key={href} href={href}>
          {label}
        </a>
      ))}
    </nav>
  );
}
