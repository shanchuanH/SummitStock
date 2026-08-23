export const recommendationActions = [
  "BUY",
  "STARTER_BUY",
  "ADD",
  "HOLD",
  "HOLD_DO_NOT_ADD",
  "WATCH",
  "WAIT_FOR_CONFIRMATION",
  "WAIT_FOR_DATA",
  "DO_NOT_ADD",
  "DO_NOT_CHASE",
  "TRIM",
  "REDUCE_HALF",
  "SELL",
  "EXIT",
  "DEPLOY_DIP_TRANCHE",
  "PAUSE_NEW_RISK",
  "REVIEW",
  "NO_ACTION",
] as const;

export type RecommendationAction = (typeof recommendationActions)[number];
export type ActionTone =
  "positive" | "neutral" | "warning" | "danger" | "blocked";
export type ActionPresentation = {
  title: string;
  shortTitle: string;
  tone: ActionTone;
  verb: string;
  requiresManualExecution: boolean;
};

export const actionPresentations = {
  BUY: {
    title: "建议买入",
    shortTitle: "买入",
    tone: "positive",
    verb: "买入",
    requiresManualExecution: true,
  },
  STARTER_BUY: {
    title: "小仓位试探买入",
    shortTitle: "试探买入",
    tone: "positive",
    verb: "买入",
    requiresManualExecution: true,
  },
  ADD: {
    title: "条件满足，可以加仓",
    shortTitle: "加仓",
    tone: "positive",
    verb: "加仓",
    requiresManualExecution: true,
  },
  HOLD: {
    title: "继续持有",
    shortTitle: "持有",
    tone: "neutral",
    verb: "持有",
    requiresManualExecution: false,
  },
  HOLD_DO_NOT_ADD: {
    title: "继续持有，但现在不要加仓",
    shortTitle: "不要加仓",
    tone: "warning",
    verb: "不加仓",
    requiresManualExecution: false,
  },
  WATCH: {
    title: "继续观察，暂不操作",
    shortTitle: "继续观察",
    tone: "neutral",
    verb: "观察",
    requiresManualExecution: false,
  },
  WAIT_FOR_CONFIRMATION: {
    title: "等待确认后再决定",
    shortTitle: "等待确认",
    tone: "blocked",
    verb: "等待",
    requiresManualExecution: false,
  },
  WAIT_FOR_DATA: {
    title: "关键数据不足，暂不判断",
    shortTitle: "等待数据",
    tone: "blocked",
    verb: "等待",
    requiresManualExecution: false,
  },
  DO_NOT_ADD: {
    title: "继续持有，但现在不要加仓",
    shortTitle: "不要加仓",
    tone: "warning",
    verb: "不加仓",
    requiresManualExecution: false,
  },
  DO_NOT_CHASE: {
    title: "价格不合适，现在不要追高",
    shortTitle: "不要追高",
    tone: "warning",
    verb: "等待",
    requiresManualExecution: false,
  },
  TRIM: {
    title: "仓位偏高，建议减持",
    shortTitle: "减持",
    tone: "danger",
    verb: "卖出",
    requiresManualExecution: true,
  },
  REDUCE_HALF: {
    title: "风险过高，建议减半",
    shortTitle: "减半",
    tone: "danger",
    verb: "卖出",
    requiresManualExecution: true,
  },
  SELL: {
    title: "建议卖出",
    shortTitle: "卖出",
    tone: "danger",
    verb: "卖出",
    requiresManualExecution: true,
  },
  EXIT: {
    title: "建议退出该仓位",
    shortTitle: "退出",
    tone: "danger",
    verb: "卖出",
    requiresManualExecution: true,
  },
  DEPLOY_DIP_TRANCHE: {
    title: "ETF 回撤计划满足一档部署条件",
    shortTitle: "部署一档",
    tone: "positive",
    verb: "买入",
    requiresManualExecution: true,
  },
  PAUSE_NEW_RISK: {
    title: "组合风险偏高，暂停新增风险",
    shortTitle: "暂停买入",
    tone: "blocked",
    verb: "等待",
    requiresManualExecution: false,
  },
  REVIEW: {
    title: "需要复核后再决定",
    shortTitle: "需要复核",
    tone: "warning",
    verb: "复核",
    requiresManualExecution: false,
  },
  NO_ACTION: {
    title: "暂无新的买卖动作",
    shortTitle: "暂无动作",
    tone: "neutral",
    verb: "持有",
    requiresManualExecution: false,
  },
} satisfies Record<RecommendationAction, ActionPresentation>;

export function isRecommendationAction(
  value: string,
): value is RecommendationAction {
  return recommendationActions.includes(value as RecommendationAction);
}

const unknownAction: ActionPresentation = {
  title: "建议状态尚未识别，暂不操作",
  shortTitle: "暂不操作",
  tone: "blocked",
  verb: "等待",
  requiresManualExecution: false,
};

export function presentAction(value?: string | null): ActionPresentation {
  return value && isRecommendationAction(value)
    ? actionPresentations[value]
    : unknownAction;
}
