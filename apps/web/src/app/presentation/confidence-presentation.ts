export const confidencePresentations = {
  HIGH: { label: "较高", detail: "关键数据完整" },
  MEDIUM: { label: "中等", detail: "存在次要不确定性" },
  LOW: { label: "较低", detail: "数据或事件风险较高" },
  WAIT_FOR_DATA: { label: "暂不判断", detail: "关键数据尚未完整" },
} as const;

export function presentConfidence(value?: string | null) {
  return value && value in confidencePresentations
    ? confidencePresentations[value as keyof typeof confidencePresentations]
    : confidencePresentations.WAIT_FOR_DATA;
}
