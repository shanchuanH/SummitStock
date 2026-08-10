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
  };
  dataAsOf?: string;
};

export type RowOverride = {
  rowNumber: number;
  symbol?: string;
  assetType?: string;
  rowType?: string;
  ignored: boolean;
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
};
