export function formatPercent(value?: string | number | null, digits = 1) {
  if (value == null || value === "") return "暂无可靠数据";
  const numeric = Number(value);
  return Number.isFinite(numeric)
    ? `${(numeric * 100).toFixed(digits)}%`
    : "暂无可靠数据";
}

export function formatMoney(value?: string | number | null, digits = 0) {
  if (value == null || value === "") return "暂无可靠数据";
  const numeric = Number(value);
  return Number.isFinite(numeric)
    ? new Intl.NumberFormat("zh-CN", {
        style: "currency",
        currency: "USD",
        maximumFractionDigits: digits,
      }).format(numeric)
    : "暂无可靠数据";
}

export function formatQuantity(min?: string | null, max?: string | null) {
  if (!min && !max) return null;
  return min === max
    ? `${String(min ?? max)} 股`
    : `${min ?? "—"}–${max ?? "—"} 股`;
}
