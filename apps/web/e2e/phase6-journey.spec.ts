import { expect, test, type Page } from "@playwright/test";

const csrf = {
  headerName: "X-CSRF-TOKEN",
  parameterName: "_csrf",
  token: "e2e-csrf",
};
const action = {
  id: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  positionId: "p1",
  symbol: "GOOGL",
  classification: "QUALITY_STOCK",
  action: "HOLD_DO_NOT_ADD",
  priority: "DO_NOT",
  quantityMin: null,
  quantityMax: null,
  currentWeight: "0.156",
  targetWeightMin: "0.08",
  targetWeightMax: "0.12",
  estimatedAmount: null,
  riskBeforeFraction: "0.025",
  riskAfterFraction: "0.02",
  confidence: "MEDIUM",
  reasonsJson:
    '["Quality remains healthy","Valuation is not discounted","Weight exceeds target"]',
  risksJson: '["Earnings volatility","Technology concentration"]',
  changeConditionsJson: '["Material earnings deterioration"]',
  dataAsOf: "2026-08-05T20:15:00Z",
  validUntil: "2026-09-01T00:00:00Z",
};
const brief = {
  state: "ANALYSIS_READY",
  headline: "Analysis ready",
  summary: {
    investedValue: "5000",
    trackedCash: "1000",
    emergencyCash: "500",
    tacticalReserve: "500",
    openPositions: 1,
  },
  mustAct: [],
  doNot: [action],
  watch: [],
  portfolioHealth: { status: "WARNING", reasons: ["Concentration"] },
  dataReadiness: {
    status: "HEALTHY",
    marketCoverage: "1",
    fundamentalCoverage: "1",
    stalePositionCount: 0,
    missingPositionCount: 0,
    failedJobCount: 0,
  },
  nextEvents: [],
  dataAsOf: "2026-08-05T20:15:00Z",
  strategyVersion: "v1",
};
const holdings = [
  {
    id: "p1",
    version: 1,
    symbol: "GOOGL",
    name: "Alphabet",
    assetType: "STOCK",
    bucket: "TACTICAL",
    classification: "QUALITY_STOCK",
    classificationConfirmed: true,
    marketValue: "5000",
    currentWeight: "0.156",
    targetWeightMin: "0.08",
    targetWeightMax: "0.12",
    action: "HOLD_DO_NOT_ADD",
    priority: "DO_NOT",
    confidence: "MEDIUM",
    trend: "ABOVE_TREND",
    nextEvent: "2026-08-20T20:00:00Z",
    dataStatus: "READY",
  },
];

async function mockApi(page: Page, initialPortfolio = true) {
  let hasPortfolio = initialPortfolio;
  await page.route("**/api/v1/**", async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    if (path === "/api/v1/auth/csrf") return route.fulfill({ json: csrf });
    if (path === "/api/v1/auth/session")
      return route.fulfill({ json: { authenticated: false, username: null } });
    if (path === "/api/v1/auth/login") return route.fulfill({ status: 204 });
    if (path === "/api/v1/brief/today")
      return route.fulfill({
        json: hasPortfolio
          ? brief
          : {
              ...brief,
              state: "NO_PORTFOLIO",
              summary: {
                ...brief.summary,
                investedValue: "0",
                trackedCash: "0",
                emergencyCash: "0",
                tacticalReserve: "0",
                openPositions: 0,
              },
              mustAct: [],
              doNot: [],
              watch: [],
            },
      });
    if (path === "/api/v1/portfolio/holdings")
      return route.fulfill({ json: holdings });
    if (path.endsWith("/acknowledge"))
      return route.fulfill({
        json: {
          recommendationId: action.id,
          decisionType: "HANDLED",
          newlyAcknowledged: true,
          executionSubmitted: false,
          acknowledgedAt: "2026-08-05T20:20:00Z",
        },
      });
    if (path.endsWith("/fidelity/preview"))
      return route.fulfill({
        json: {
          batchId: "batch-1",
          status: "PREVIEWED",
          version: 0,
          accounts: [
            { accountName: "Brokerage", accountNumberMasked: "***1234" },
          ],
          holdings: [
            {
              rowNumber: 2,
              accountName: "Brokerage",
              accountNumberMasked: "***1234",
              symbol: "GOOGL",
              assetType: "EQUITY",
              quantity: "10",
              currentValue: "2000",
              costBasis: "1500",
              rowType: "HOLDING",
              status: "VALID",
              warnings: [],
            },
          ],
          cash: [],
          warnings: [],
          errors: [],
          summary: {
            rowCount: 1,
            validRowCount: 1,
            errorRowCount: 0,
            estimatedInvestedValue: "2000",
            estimatedCashValue: "0",
          },
        },
      });
    if (path.endsWith("/confirm")) {
      hasPortfolio = true;
      return route.fulfill({
        json: {
          batchId: "batch-1",
          status: "CONFIRMED",
          version: 1,
          analysisRunId: "run-1",
          analysisState: "ANALYSIS_QUEUED",
          openPositionCount: 1,
          closedPositionCount: 0,
          cashRowCount: 0,
          compensationRowCount: 0,
          idempotentReplay: false,
        },
      });
    }
    if (path.endsWith("/report"))
      return route.fulfill({
        json: {
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
            quantityMin: null,
            quantityMax: null,
            currentWeight: action.currentWeight,
            targetWeightMin: action.targetWeightMin,
            targetWeightMax: action.targetWeightMax,
            confidence: action.confidence,
            reasons: ["Quality remains healthy"],
            risks: ["Technology concentration"],
            changeConditions: ["Material earnings deterioration"],
            winningRule: "RISK_CAP",
            suppressedCandidates: [],
            resolutionReason: "Position is above target.",
            validUntil: action.validUntil,
          },
          evidence: {
            analysisStatus: "READY",
            exactQuantityAllowed: false,
            ruleIds: ["RISK_CAP"],
            strategyVersion: "v1",
            configHash: "hash",
          },
          dataAsOf: action.dataAsOf,
        },
      });
    if (path.endsWith("/chart"))
      return route.fulfill({
        json: {
          bars: [],
          entryMarkers: [],
          stopSeries: [],
          earningsMarkers: [],
          tradeMarkers: [],
          dataAsOf: action.dataAsOf,
          quality: "MISSING",
        },
      });
    if (path.endsWith("/journal")) return route.fulfill({ json: [] });
    return route.fulfill({
      status: 404,
      json: { detail: `Unhandled ${path}` },
    });
  });
}

test("login, empty portfolio, upload, preview, confirm, and analysis pending", async ({
  page,
}) => {
  await mockApi(page, false);
  await page.goto("/settings");
  await page.getByLabel("Email").fill("admin@example.local");
  await page.getByLabel("Password").fill("password");
  await page.getByRole("button", { name: "Sign in" }).click();
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "今日简报" })).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "尚未导入投资组合" }),
  ).toBeVisible();
  await page.goto("/portfolio/import");
  await page
    .locator('input[type="file"]')
    .setInputFiles({
      name: "positions.csv",
      mimeType: "text/csv",
      buffer: Buffer.from("Symbol,Quantity\nGOOGL,10"),
    });
  await expect(page.getByText("GOOGL")).toBeVisible();
  await expect(page.getByText("1 source rows")).toBeVisible();
  await page
    .getByRole("button", { name: "Confirm and queue analysis" })
    .click();
  await expect(
    page.getByRole("heading", { name: "Analysis has been queued" }),
  ).toBeVisible();
  await expect(page.getByText("ANALYSIS_QUEUED")).toBeVisible();
});

test("analysis ready, acknowledge without execution, and open position", async ({
  page,
}) => {
  await mockApi(page);
  await page.goto("/");
  await expect(page.getByText("HOLD_DO_NOT_ADD")).toBeVisible();
  await page.getByRole("button", { name: "我已处理" }).click();
  await expect(page.getByText("这不会在券商账户执行交易。")).toBeVisible();
  await page.goto("/portfolio");
  await page.getByRole("link", { name: /GOOGL/ }).click();
  await expect(page.getByRole("heading", { name: "GOOGL" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "最终结论" })).toBeVisible();
  await expect(page.getByText("暂无可用价格图表")).toBeVisible();
});
