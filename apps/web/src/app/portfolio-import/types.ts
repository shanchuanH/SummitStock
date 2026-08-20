export type ImportRowStatus = "VALID" | "WARNING" | "ERROR" | "IGNORED";

export type ImportedHolding = {
  rowNumber: number;
  accountName?: string;
  accountNumberMasked?: string;
  symbol?: string;
  description?: string;
  assetType?: string;
  quantity?: string;
  lastPrice?: string;
  currentValue?: string;
  averageCost?: string;
  costBasis?: string;
  rowType: string;
  status: ImportRowStatus;
  warnings: string[];
  suggestedClassification?: string;
  classificationReason?: string;
};

export type ImportPreview = {
  batchId: string;
  status: string;
  version: number;
  accounts: Array<{ accountName?: string; accountNumberMasked?: string }>;
  holdings: ImportedHolding[];
  cash: ImportedHolding[];
  warnings: string[];
  errors: string[];
  summary: {
    rowCount: number;
    validRowCount: number;
    errorRowCount: number;
    estimatedInvestedValue: string;
    estimatedCashValue: string;
    emergencyCashTarget: string;
  };
  dataAsOf?: string;
};

export type RowOverride = {
  rowNumber: number;
  symbol?: string;
  assetType?: string;
  rowType?: string;
  classification?: string;
  ignored: boolean;
};

export type CashSetup = {
  location: "IN_FIDELITY" | "EXTERNAL_BANK" | "SPLIT" | "BELOW_TARGET";
  amount: string;
};

export type ImportConfirmation = {
  batchId: string;
  status: string;
  version: number;
  analysisRunId: string;
  analysisState: string;
  openPositionCount: number;
  closedPositionCount: number;
  cashRowCount: number;
  compensationRowCount: number;
  idempotentReplay: boolean;
  cashflowReconciliation: CashflowReconciliation;
};

export type CashflowReconciliation = {
  reconciliationId?: string;
  status: string;
  cashChange: string;
};

export type AnalysisStatus = {
  runId: string;
  state: string;
  worker: { alive: boolean; lastSeenAt?: string };
  progress: {
    completed: number;
    total: number;
    currentStage?: string;
    lastProgressAt: string;
  };
  failure?: {
    failedStage: string;
    errorCode?: string;
    errorMessage: string;
    retryable: boolean;
  };
  pendingAgeSeconds?: number;
  startedAt?: string;
  estimatedCategory?: string;
  stages: Array<{ code: string; label: string; status: string }>;
};
