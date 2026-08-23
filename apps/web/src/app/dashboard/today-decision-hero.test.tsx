import type { components } from "@portfolio/api-client";
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { TodayDecisionHero } from "./TodayDecisionHero";

type Brief = components["schemas"]["ExecutiveBrief"];
function brief(overrides: Partial<Brief> = {}): Brief {
  return {
    state: "ANALYSIS_READY",
    confirmedNoAction: true,
    headline: "Ready",
    summary: {
      investedValue: "1",
      trackedCash: "0",
      emergencyCash: "0",
      tacticalReserve: "0",
      openPositions: 1,
      totalLiquidAssets: "1",
      unvestedCompensationValue: "0",
    },
    market: {
      regime: "GREEN",
      confidence: "HIGH",
      qualityStatus: "HEALTHY",
      summary: "Healthy",
    },
    capital: {
      totalLiquidAssets: "1",
      requiredEmergencyFloor: "0",
      emergencyReserve: "0",
      deployableCash: "0",
      investableAssets: "1",
      tacticalReserve: "0",
    },
    portfolio: {},
    mustAct: [],
    doNot: [],
    watch: [],
    opportunities: [],
    blocked: [],
    todayPriorities: [],
    topRisks: [],
    allHoldings: [],
    portfolioHealth: { status: "HEALTHY", reasons: [] },
    dataReadiness: {
      status: "READY",
      marketCoverage: "1",
      fundamentalCoverage: "1",
      completeness: "1",
      stalePositionCount: 0,
      missingPositionCount: 0,
      failedJobCount: 0,
    },
    nextEvents: [],
    ...overrides,
  };
}

describe("TodayDecisionHero", () => {
  it("leads with deterministic urgent actions", () => {
    const action = {
      id: "11111111-1111-1111-1111-111111111111",
      symbol: "DXYZ",
      action: "EXIT",
      priority: "MUST_ACT",
      riskCalculationReason: "READY",
      taxLotStatus: "NOT_APPLICABLE",
      confidence: "HIGH",
      reasonsJson: "[]",
      risksJson: "[]",
      changeConditionsJson: "[]",
      dataAsOf: "2026-08-13T00:00:00Z",
      validUntil: "2026-08-14T00:00:00Z",
    };
    render(
      <TodayDecisionHero
        brief={brief({
          confirmedNoAction: false,
          mustAct: [action],
          todayPriorities: [action],
          allHoldings: [
            {
              positionId: "p1",
              symbol: "DXYZ",
              companyName: "Destiny",
              action: "EXIT",
              priority: "MUST_ACT",
              confidence: "HIGH",
              dataStatus: "READY",
            },
          ],
        })}
      />,
    );
    expect(
      screen.getByRole("heading", { name: "今天有 1 件事需要你处理" }),
    ).toBeInTheDocument();
    expect(screen.getByText(/DXYZ/).closest("li")).toHaveTextContent("退出");
  });

  it("never presents partial analysis as calm", () => {
    render(
      <TodayDecisionHero
        brief={brief({
          state: "PARTIAL_ANALYSIS",
          confirmedNoAction: false,
          dataReadiness: {
            status: "PARTIAL",
            marketCoverage: "1",
            fundamentalCoverage: "0.7",
            completeness: "0.7",
            stalePositionCount: 0,
            missingPositionCount: 3,
            failedJobCount: 0,
          },
        })}
      />,
    );
    expect(
      screen.getByRole("heading", {
        name: "今天先不要根据 SummitStock 下新决定",
      }),
    ).toBeInTheDocument();
    expect(screen.queryByText("今天不需要做交易")).not.toBeInTheDocument();
  });
});
