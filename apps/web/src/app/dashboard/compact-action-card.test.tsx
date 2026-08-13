import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ActionCard, type DashboardAction } from "./ActionCard";

function renderCard(overrides: Partial<DashboardAction> = {}) {
  const action: DashboardAction = {
    id: "11111111-1111-1111-1111-111111111111",
    symbol: "GOOGL",
    action: "DO_NOT_ADD",
    priority: "DO_NOT",
    riskCalculationReason: "PROJECTED_RISK_INPUT_MISSING",
    taxLotStatus: "NOT_APPLICABLE",
    confidence: "MEDIUM",
    reasonsJson: '["仓位已经超过正常上限。"]',
    risksJson: '["单一个股集中度较高。"]',
    changeConditionsJson: '["仓位降回正常范围。"]',
    dataAsOf: "2026-08-13T00:00:00Z",
    validUntil: "2026-08-14T00:00:00Z",
    ...overrides,
  };
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <ol>
        <ActionCard action={action} />
      </ol>
    </QueryClientProvider>,
  );
}

describe("compact owner action card", () => {
  it("distinguishes do-not-add from a sell and explains missing quantity", () => {
    renderCard();
    expect(
      screen.getByRole("heading", { name: "继续持有，但现在不要加仓" }),
    ).toBeInTheDocument();
    expect(screen.getByText(/暂不提供精确股数/)).toBeInTheDocument();
    expect(screen.queryByText("DO_NOT_ADD")).not.toBeInTheDocument();
    expect(screen.queryByText(/建议卖出/)).not.toBeInTheDocument();
  });
});
