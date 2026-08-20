export const readinessPresentations = {
  READY: {
    label: "分析完整",
    detail: "关键数据已完成，可以使用当前结论。",
    tone: "positive",
  },
  HEALTHY: {
    label: "分析完整",
    detail: "关键数据已完成，可以使用当前结论。",
    tone: "positive",
  },
  PARTIAL: {
    label: "部分数据不足",
    detail: "已有结论不会被当作完整建议。",
    tone: "warning",
  },
  STALE: {
    label: "数据需要更新",
    detail: "等待最新数据后再形成新的精确建议。",
    tone: "warning",
  },
  WAIT_FOR_DATA: {
    label: "等待关键数据",
    detail: "系统不会用缺失数据生成假精确结论。",
    tone: "blocked",
  },
  WAIT_FOR_MARKET_DATA: {
    label: "等待市场数据",
    detail: "最新价格尚未确认，暂不提供精确数量。",
    tone: "blocked",
  },
  WAIT_FOR_FUNDAMENTALS: {
    label: "等待公司财务数据",
    detail: "基本面分析尚未完成。",
    tone: "blocked",
  },
  WAIT_FOR_CATALYST: {
    label: "等待明确催化剂",
    detail: "价格信号不能代替催化剂证据；确认前只观察，不加仓。",
    tone: "blocked",
  },
  WAIT_FOR_CLASSIFICATION: {
    label: "等待确认组合角色",
    detail: "确认持仓角色后才能应用正确的策略限制。",
    tone: "blocked",
  },
  MISSING: {
    label: "数据尚未建立",
    detail: "完成导入与分析后才会形成建议。",
    tone: "blocked",
  },
  BLOCKED: {
    label: "分析受阻",
    detail: "关键条件未满足，暂不形成新的交易建议。",
    tone: "danger",
  },
  FAILED: {
    label: "分析未完成",
    detail: "已有结论可能不完整，暂不提供新的精确数量。",
    tone: "danger",
  },
} as const;

export function presentReadiness(value?: string | null) {
  return value && value in readinessPresentations
    ? readinessPresentations[value as keyof typeof readinessPresentations]
    : {
        label: "分析状态待确认",
        detail: "在状态确认前请不要依据系统下新决定。",
        tone: "blocked" as const,
      };
}
