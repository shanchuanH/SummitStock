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
    if (path.endsWith("/fidelity/preview"))
      return route.fulfill({
        json: {
          batchId: "11111111-1111-1111-1111-111111111111",
          status: "PREVIEW",
          version: 0,
          accounts: [
            {
              accountName: "Primary Brokerage",
              accountNumberMasked: "***5678",
            },
          ],
          holdings: [
            {
              rowNumber: 2,
              accountName: "Primary Brokerage",
              accountNumberMasked: "***5678",
              symbol: "GOOGL",
              description: "Alphabet",
              assetType: "EQUITY",
              quantity: "10",
              currentValue: "2000",
              costBasis: "1500",
              rowType: "HOLDING",
              status: "VALID",
              warnings: [],
              suggestedClassification: "QUALITY_STOCK",
              classificationReason: "Confirmed quality-stock seed.",
            },
            {
              rowNumber: 3,
              accountName: "Primary Brokerage",
              accountNumberMasked: "***5678",
              symbol: "SPY",
              description: "SPDR ETF",
              assetType: "ETF",
              quantity: "5",
              currentValue: "2500",
              costBasis: "2200",
              rowType: "HOLDING",
              status: "VALID",
              warnings: [],
              suggestedClassification: "CORE_BROAD_ETF",
              classificationReason: "Recognized broad-market ETF.",
            },
          ],
          cash: [
            {
              rowNumber: 4,
              accountName: "Primary Brokerage",
              accountNumberMasked: "***5678",
              symbol: "SPAXX",
              currentValue: "20000",
              rowType: "CASH",
              status: "VALID",
              warnings: [],
            },
          ],
          warnings: [],
          errors: [],
          summary: {
            rowCount: 3,
            validRowCount: 3,
            errorRowCount: 0,
            estimatedInvestedValue: "4500",
            estimatedCashValue: "20000",
            emergencyCashTarget: "20000",
          },
        },
      });
    if (path.endsWith("/confirm")) {
      expect(route.request().postDataJSON()).toMatchObject({
        cashSetup: { location: "IN_FIDELITY", amount: "20000" },
      });
      return route.fulfill({
        json: {
          batchId: "11111111-1111-1111-1111-111111111111",
          status: "CONFIRMED",
          version: 1,
          analysisRunId: "22222222-2222-2222-2222-222222222222",
          analysisState: "ANALYSIS_QUEUED",
          openPositionCount: 2,
          closedPositionCount: 0,
          cashRowCount: 1,
          compensationRowCount: 0,
          idempotentReplay: false,
        },
      });
    }
    if (path.includes("/analysis/status/"))
      return route.fulfill({
        json: {
          runId: "22222222-2222-2222-2222-222222222222",
          state: "ANALYSIS_RUNNING",
          completedStages: 2,
          totalStages: 8,
          updatedAt: action.dataAsOf,
          stages: [
            { code: "HOLDINGS", label: "持仓导入", status: "COMPLETE" },
            { code: "PRICES", label: "价格", status: "COMPLETE" },
            { code: "FINANCIALS", label: "财务数据", status: "RUNNING" },
            { code: "VALUATION", label: "估值", status: "WAITING" },
            { code: "EVENTS", label: "分析师预测与财报", status: "WAITING" },
            { code: "PORTFOLIO_RISK", label: "组合风险", status: "WAITING" },
            { code: "HOLDING_ANALYSIS", label: "逐股分析", status: "WAITING" },
            { code: "TODAY_BRIEF", label: "今日简报", status: "WAITING" },
          ],
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
    page.getByRole("heading", { name: "今天需要处理的动作" }),
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
  await expect(page.getByText("仓位已超过目标。").first()).toBeVisible();
  await expect(page.getByText("硬上限").first()).toBeVisible();
  await page.getByRole("heading", { name: "完整证据" }).click();
  await expect(page.getByText(/RISK_CAP/)).toBeVisible();
});

test("final owner journey imports stock, ETF and cash before analysis and decision review", async ({
  page,
}, testInfo) => {
  const mobile = testInfo.project.name === "mobile-chromium";
  if (mobile) {
    await page.setViewportSize({ width: 375, height: 812 });
  }
  await page.goto("/portfolio/import");
  await page.locator('input[type="file"]').setInputFiles({
    name: "positions.csv",
    mimeType: "text/csv",
    buffer: Buffer.from("fidelity export"),
  });
  const recognizedRows = page.locator("details.recognized-import-rows");
  await recognizedRows.locator("summary").click();
  await expect(recognizedRows.getByText("GOOGL")).toBeVisible();
  await expect(recognizedRows.getByText("SPY")).toBeVisible();
  await expect(recognizedRows.getByText("SPAXX")).toBeVisible();
  await expect(recognizedRows).toContainText("2000");
  await expect(recognizedRows).toContainText("2500");
  if (mobile) {
    expect(
      await page.evaluate(
        () =>
          document.documentElement.scrollWidth <=
          document.documentElement.clientWidth,
      ),
    ).toBe(true);
  }
  await page.getByRole("button", { name: "继续确认角色" }).click();
  if (mobile) {
    await expect(
      page.locator('.import-role-card[data-mobile-active="true"]'),
    ).toHaveCount(1);
    await expect(
      page
        .locator('.import-role-card[data-mobile-active="true"]')
        .getByText(/已自动识别；如不正确/),
    ).toBeVisible();
    await page.getByRole("button", { name: "下一个持仓" }).click();
    await expect(
      page
        .locator('.import-role-card[data-mobile-active="true"]')
        .getByText(/已自动识别；如不正确/),
    ).toBeVisible();
    expect(
      await page.evaluate(
        () =>
          document.documentElement.scrollWidth <=
          document.documentElement.clientWidth,
      ),
    ).toBe(true);
  }
  await page.getByRole("button", { name: "继续设置备用金" }).click();
  await expect(page.getByLabel("确认生活备用金金额")).toHaveValue("20000");
  await expect(
    page.getByText(/已从本次 Fidelity 文件自动识别并填入/),
  ).toBeVisible();
  await page.getByRole("button", { name: "继续最终确认" }).click();
  const confirmButton = page.getByRole("button", { name: "确认并开始分析" });
  await expect(confirmButton).toBeInViewport();
  await confirmButton.click();
  await expect(page.getByRole("heading", { name: "分析已开始" })).toBeVisible();
  await expect(
    page.getByRole("list", { name: "Analysis progress" }).getByRole("listitem"),
  ).toHaveCount(8);
  await page.goto("/");
  await expect(page.locator(".action-card")).toHaveCount(1);
  await page.goto("/positions/p1");
  await expect(page.getByText("15.0%").first()).toBeVisible();
  await page.getByRole("heading", { name: "价格 / 风险 / 财报" }).click();
  await expect(page.getByText(/财报风险/)).toBeVisible();
  await page.getByRole("heading", { name: "完整证据" }).click();
  await expect(page.getByText(/什么情况下建议会改变/)).toBeVisible();
});

test("incomplete analysis never presents false calm", async ({ page }) => {
  await page.route("**/api/v1/brief/today", (route) =>
    route.fulfill({
      json: {
        ...brief,
        state: "PARTIAL_ANALYSIS",
        confirmedNoAction: false,
        headline: "分析尚未完成，部分结论暂不可用。",
        todayPriorities: [],
        mustAct: [],
        doNot: [],
        watch: [],
      },
    }),
  );

  await page.goto("/");
  await expect(page.getByText("当前无需紧急操作")).toHaveCount(0);
  await expect(
    page.getByText(/今天先不要根据 SummitStock 下新决定/).first(),
  ).toBeVisible();
});
