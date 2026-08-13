import type { PortfolioHolding } from "../portfolio-page";
import { presentAction } from "../presentation/action-presentation";
import { presentClassification } from "../presentation/classification-presentation";
import { formatDate } from "../presentation/date-format";
import { formatMoney, formatPercent } from "../presentation/number-format";
import { presentPriority } from "../presentation/priority-presentation";
import { presentReadiness } from "../presentation/readiness-presentation";

function targetRange(row: PortfolioHolding) {
  return row.targetWeightMin == null && row.targetWeightMax == null
    ? "目标待分析"
    : `${formatPercent(row.targetWeightMin)}–${formatPercent(row.targetWeightMax)}`;
}

export function HoldingCard({
  holding,
  onConfirmClassification,
}: {
  holding: PortfolioHolding;
  onConfirmClassification: (holding: PortfolioHolding) => void;
}) {
  const action = presentAction(holding.action);
  const priority = presentPriority(holding.priority);
  return (
    <tr className="holding-card" data-priority={holding.priority}>
      <td className="holding-identity" data-label="持仓">
        <a href={`/positions/${String(holding.id)}`}>
          <strong>{holding.symbol}</strong>
          <span>{holding.name}</span>
        </a>
        <small>
          {presentClassification(holding.classification)} ·{" "}
          {formatMoney(holding.marketValue, 2)}
        </small>
        {!holding.classificationConfirmed ? (
          <button
            className="classification-button"
            onClick={() => {
              onConfirmClassification(holding);
            }}
          >
            确认组合角色
          </button>
        ) : null}
      </td>
      <td className="holding-action" data-label="系统建议">
        <span className={`priority-pill ${priority.tone}`}>
          {action.shortTitle}
        </span>
      </td>
      <td className="holding-weight" data-label="仓位">
        <strong>{formatPercent(holding.currentWeight)}</strong>
        <small>目标 {targetRange(holding)}</small>
      </td>
      <td className="holding-reason" data-label="关键原因">
        {holding.keyReason ?? "当前建议的详细依据尚未形成"}
      </td>
      <td data-label="下一个事件">{formatDate(holding.nextEvent)}</td>
      <td data-label="数据状态">
        {presentReadiness(holding.dataStatus).label}
        <a
          className="holding-analysis-link"
          href={`/positions/${String(holding.id)}`}
        >
          查看分析
        </a>
      </td>
    </tr>
  );
}
