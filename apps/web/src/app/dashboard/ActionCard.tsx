import type { components } from "@portfolio/api-client";

type BriefAction = components["schemas"]["BriefAction"];

export function ActionCard({ action }: { action: BriefAction }) {
  return (
    <li>
      <strong>{action.symbol ?? "UNKNOWN"}</strong>
      <span>{action.action}</span>
      <small>
        {action.priority} · {action.confidence}
      </small>
    </li>
  );
}
