export const priorityPresentations = {
  MUST_ACT: { label: "今天处理", tone: "danger" },
  DO_NOT: { label: "现在不要做", tone: "warning" },
  WATCH: { label: "继续观察", tone: "neutral" },
  NORMAL: { label: "暂无动作", tone: "neutral" },
} as const;

export function presentPriority(value?: string | null) {
  return value && value in priorityPresentations
    ? priorityPresentations[value as keyof typeof priorityPresentations]
    : { label: "等待分析", tone: "blocked" as const };
}
