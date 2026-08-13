import { expect, test, type Page } from "@playwright/test";

const action = {
  id: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  positionId: "p1",
  symbol: "GOOGL",
  companyName: "Alphabet",
  classification: "QUALITY_STOCK",
  action: "HOLD_DO_NOT_ADD",
  priority: "DO_NOT",
  quantityMin: null,
  quantityMax: null,
  currentWeight: "0.156",
  targetWeightMin: "0.04",
  targetWeightMax: "0.08",
  estimatedAmount: null,
  riskBeforeFraction: "0.025",
  riskAfterFraction: "0.02",
  riskCalculationReason: "CALCULATED",
  taxLotStatus: "NOT_APPLICABLE",
  confidence: "MEDIUM",
  reasonsJson: '["公司基本面健康，但仓位已超过目标。"]',
  risksJson: '["科技板块集中度较高。"]',
  changeConditionsJson: '["盈利趋势显著恶化"]',
  dataAsOf: "2026-08-05T20:15:00Z",
  validUntil: "2026-09-01T00:00:00Z",
};
const holding = {
  positionId: "p1",
  symbol: "GOOGL",
  companyName: "Alphabet",
  classification: "QUALITY_STOCK",
  action: "HOLD_DO_NOT_ADD",
  priority: "DO_NOT",
  confidence: "MEDIUM",
  currentWeight: "0.156",
  dataStatus: "READY",
};
const brief = {
  state: "ANALYSIS_READY",
  confirmedNoAction: false,
  headline: "组合分析完成，今天有一项需要避免的操作。",
  summary: {
    investedValue: "5000",
    trackedCash: "1000",
    emergencyCash: "500",
    tacticalReserve: "500",
    openPositions: 1,
    totalLiquidAssets: "6000",
    coreExposureFraction: "0",
    tacticalExposureFraction: "0.83",
    tacticalSpeculativeExposureFraction: "0",
    unvestedCompensationValue: "0",
  },
  market: {
    regime: "YELLOW",
    confidence: "MEDIUM",
    qualityStatus: "HEALTHY",
    summary: "市场处于中性状态。",
  },
  capital: {
    totalLiquidAssets: "6000",
    emergencyReserve: "500",
    deployableCash: "500",
    investableAssets: "5500",
    tacticalReserve: "500",
  },
  portfolio: {
    drawdown: "0.02",
    drawdownSource: "MARKET_DRIVEN",
    technologyExposure: "0.5",
    openRisk: "0.025",
    clusterRisk: "0.025",
  },
  mustAct: [],
  doNot: [action],
  watch: [],
  opportunities: [],
  blocked: [],
  todayPriorities: [action],
  topRisks: [
    {
      risk: "科技板块集中度较高。",
      meaning: "同一风险因子可能同时影响多个持仓。",
      nowAction: "不要继续增加科技敞口。",
      symbol: "GOOGL",
      priority: "DO_NOT",
    },
  ],
  allHoldings: [holding],
  portfolioHealth: { status: "WARNING", reasons: ["科技集中度"] },
  dataReadiness: {
    status: "HEALTHY",
    marketCoverage: "1",
    fundamentalCoverage: "1",
    completeness: "1",
    stalePositionCount: 0,
    missingPositionCount: 0,
    failedJobCount: 0,
  },
  nextEvents: [],
  dataAsOf: "2026-08-05T20:15:00Z",
  strategyVersion: "3.0.0-draft",
};
const report = {
  position: {
    id: "p1",
    symbol: "GOOGL",
    classification: "QUALITY_STOCK",
    classificationSource: "USER_CONFIRMED",
  },
  readiness: "READY",
  recommendation: {
    id: action.id,
    action: action.action,
    priority: action.priority,
    currentWeight: action.currentWeight,
    targetWeightMin: action.targetWeightMin,
    targetWeightMax: action.targetWeightMax,
    confidence: action.confidence,
    reasons: ["公司基本面健康"],
    risks: ["科技集中度"],
    changeConditions: ["盈利趋势恶化"],
    resolutionReason: "仓位已超过目标。",
    validUntil: action.validUntil,
  },
  evidence: {
    analysisStatus: "READY",
    exactQuantityAllowed: false,
    ruleIds: ["RISK_CAP"],
    strategyVersion: "3.0.0-draft",
    configHash: "abc123",
  },
  layers: {
    systemRecommendation: {
      action: action.action,
      priority: action.priority,
      confidence: action.confidence,
      exactQuantityAllowed: false,
    },
    portfolioRole: {
      classification: "QUALITY_STOCK",
      currentWeight: "0.156",
      targetWeightMin: "0.04",
      targetWeightMax: "0.08",
      normalMaxWeight: "0.12",
      hardMaxWeight: "0.15",
      atHardMax: true,
      capacityExplanation: "已触及组合硬上限。",
    },
    fundamentals: {
      financialHealth: "HEALTHY",
      quality: "HEALTHY",
      available: true,
    },
    valuation: {
      state: "ATTRACTIVE",
      confidence: "MEDIUM",
      observationCount: 12,
      independentConfirmation: true,
      attractive: true,
      attractiveButCannotAdd: true,
    },
    priceRiskEarnings: {
      priceState: "UPTREND",
      formalStop: "170",
      liveStop: "175",
      earningsRisk: "ELEVATED",
    },
    rationaleAndEvidence: {
      reasons: ["公司基本面健康"],
      risks: ["科技集中度"],
      changeConditions: ["盈利趋势恶化"],
      evidenceDrawer: {
        ruleIds: ["RISK_CAP"],
        evidenceRefs: ["financial-health:GOOGL"],
        strategyVersion: "3.0.0-draft",
        configHash: "abc123",
        dataQuality: "HEALTHY",
        dataAsOf: action.dataAsOf,
      },
    },
  },
  dataAsOf: action.dataAsOf,
};

async function mockApi(page: Page) {
  await page.route("**/api/v1/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === "/api/v1/brief/today") return route.fulfill({ json: brief });
    if (path === "/api/v1/auth/csrf")
      return route.fulfill({
        json: {
          headerName: "X-CSRF-TOKEN",
          parameterName: "_csrf",
          token: "e2e",
        },
      });
    if (path.endsWith("/acknowledge"))
      return route.fulfill({
        json: {
          recommendationId: action.id,
          decisionType: "HANDLED",
          newlyAcknowledged: true,
          executionSubmitted: false,
          acknowledgedAt: action.dataAsOf,
        },
      });
    if (path.endsWith("/analyst-report"))
      return route.fulfill({ json: report });
    if (path.endsWith("/intelligence")) return route.fulfill({ json: {} });
    if (path.endsWith("/chart"))
      return route.fulfill({
        json: {
          bars: [],
          entryMarkers: [],
          stopSeries: [],
          earningsMarkers: [],
          tradeMarkers: [],
          quality: "MISSING",
        },
      });
    return route.fulfill({
      status: 404,
      json: { code: "RESOURCE_NOT_FOUND", nextAction: "返回组合后重试。" },
    });
  });
}

test.beforeEach(async ({ page }) => mockApi(page));

test("owner brief is actionable, complete, and never submits a trade", async ({
  page,
}) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "今日简报" })).toBeVisible();
  await expect(page.getByText("数据完整 · 100%")).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "今日优先动作" }),
  ).toBeVisible();
  await expect(page.getByRole("button", { name: "我已处理" })).toBeVisible();
  await expect(page.getByRole("button", { name: "暂不处理" })).toBeVisible();
  await expect(page.getByRole("button", { name: /交易|下单/ })).toHaveCount(0);
  await page.getByRole("button", { name: "我已处理" }).click();
  await expect(page.getByRole("status")).toHaveText("处理决定已记录。");
  expect(
    await page.evaluate(
      () =>
        document.documentElement.scrollWidth <=
        document.documentElement.clientWidth,
    ),
  ).toBe(true);
});

test("holding report leads with the decision and keeps technical evidence in a drawer", async ({
  page,
}) => {
  await page.goto("/positions/p1");
  await expect(page.getByRole("heading", { name: "GOOGL" })).toBeVisible();
  await expect(page.locator(".module-number")).toHaveCount(6);
  await expect(
    page.getByText(/估值可能有吸引力，但组合仓位已触及上限/),
  ).toBeVisible();
  const drawer = page.getByText("查看证据与审计信息");
  await expect(drawer).toBeVisible();
  await drawer.click();
  await expect(page.getByText(/RISK_CAP/)).toBeVisible();
});
