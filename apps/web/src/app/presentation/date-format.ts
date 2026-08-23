export function formatDate(value?: string | null) {
  if (!value) return "日期待确认";
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? "日期待确认"
    : date.toLocaleDateString("zh-CN");
}

export function formatDateTime(value?: string | null) {
  if (!value) return "时间待确认";
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? "时间待确认"
    : date.toLocaleString("zh-CN");
}
