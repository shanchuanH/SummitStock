export const classificationPresentations = {
  CORE_BROAD_ETF: "全市场核心 ETF",
  CORE_TECH_ETF: "科技核心 ETF",
  QUALITY_STOCK: "优质公司核心仓",
  QUALITY_GROWTH_HIGH_VOL: "优质成长股 · 高波动",
  THEMATIC_ETF: "主题 ETF · 主动仓",
  TACTICAL_STOCK: "主动 / 战术仓",
  CYCLICAL_TACTICAL: "战术仓 · 周期型",
  TURNAROUND_TACTICAL: "战术仓 · 反转型",
  SPECULATIVE: "投机仓 · 严格限额",
  CASH_EQUIVALENT: "现金及等价物",
  UNVESTED_COMPENSATION: "未归属公司股票 · 不可交易",
  UNKNOWN: "组合角色待确认",
} as const;

export function presentClassification(value?: string | null) {
  return value && value in classificationPresentations
    ? classificationPresentations[
        value as keyof typeof classificationPresentations
      ]
    : "组合角色待确认";
}
