export function DataCompletenessBadge({ value }: { value: string }) {
  const percentage = Math.max(0, Math.min(100, Number(value) * 100));
  const label =
    percentage === 100
      ? "数据完整"
      : percentage >= 80
        ? "数据基本完整"
        : "数据不完整";
  return (
    <span
      className="data-completeness-badge"
      data-status={percentage === 100 ? "complete" : "partial"}
    >
      {label} · {percentage.toFixed(0)}%
    </span>
  );
}
