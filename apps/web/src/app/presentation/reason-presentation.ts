const reasonPresentations: Record<string, string> = {
  POSITION_TOO_LARGE: "仓位已经超过策略的正常上限。",
  EMERGENCY_CASH_NOT_READY: "生活备用金尚未达到保护目标。",
  PORTFOLIO_RISK_FULL: "组合的计划风险额度已经用满。",
  CLUSTER_RISK_FULL: "相同风险来源的仓位已经过于集中。",
  VALUATION_TOO_HIGH: "当前估值不足以支持继续买入。",
  VALUATION_ATTRACTIVE: "当前估值具有吸引力。",
  FUNDAMENTALS_HEALTHY: "公司基本面仍然健康。",
  FUNDAMENTALS_WEAKENING: "公司基本面出现弱化迹象。",
  EARNINGS_RISK_HIGH: "财报临近，跳空风险较高。",
  CATALYST_MISSING: "价格已经改善，但缺少明确催化剂。",
  CATALYST_CONFIRMED: "明确催化剂已经得到确认。",
  PRICE_NOT_CONFIRMED: "价格尚未形成策略要求的确认信号。",
  STOP_TRIGGERED: "正式退出条件已经触发。",
  DATA_STALE: "关键数据需要更新。",
  PRICE_DATA_STALE: "今天的收盘价数据尚未确认。",
  RISK_DATA_INCOMPLETE: "组合风险数据尚未完整。",
  CLASSIFICATION_UNCONFIRMED: "持仓在组合中的角色尚未确认。",
  TAX_LOTS_MISSING: "税务批次信息尚未完整。",
  TAX_DATA_MISSING: "税务信息尚未完整。",
  EARNINGS_TOO_CLOSE: "距离财报太近，暂不增加风险。",
  EMERGENCY_CASH_BELOW_FLOOR: "生活备用金低于保护目标。",
  ETF_DIP_WAITING_FOR_CONFIRMATION: "ETF 回撤条件仍在等待价格确认。",
  PROJECTED_RISK_INPUT_MISSING: "缺少完整风险快照，无法可靠计算新增仓位。",
  ESTIMATE_DATA_MISSING: "缺少分析师盈利预测。",
  VALUATION_HISTORY_INSUFFICIENT: "历史估值样本不足。",
};

export function presentReason(value?: string | null) {
  if (!value) return "当前没有足够证据形成完整原因。";
  return reasonPresentations[value] ?? "当前证据不足以支持可靠的精确数量。";
}
