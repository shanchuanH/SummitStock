export type DataQuality =
  "HEALTHY" | "PARTIAL" | "STALE" | "MISSING" | "FAILED";

export const dataQualityPresentation = {
  HEALTHY: "完整",
  PARTIAL: "部分可用",
  STALE: "需要更新",
  MISSING: "数据待补",
  FAILED: "获取失败",
} satisfies Record<DataQuality, string>;

export function presentDataQuality(value?: string | null): string {
  return value && value in dataQualityPresentation
    ? dataQualityPresentation[value as DataQuality]
    : "状态未知";
}
