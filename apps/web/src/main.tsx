import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { RouterProvider, createBrowserRouter } from "react-router";
import { HealthPage } from "./app/health-page";
import { MarketContextPage } from "./app/market-context-page";
import { MarketInspectionPage } from "./app/market-inspection-page";
import { PortfolioPage } from "./app/portfolio-page";
import { PositionDetailPage } from "./app/position-detail-page";
import { DipPage } from "./app/dip-page";
import { BacktestPage } from "./app/backtest-page";
import { PortfolioImportPage } from "./app/portfolio-import/PortfolioImportPage";
import { RuntimeLayout } from "./app/runtime-layout";
import {
  DashboardPage,
  AdvancedResearchPage,
  DataHealthPage,
  JournalPage,
  OpportunitiesPage,
  ReviewPage,
  SettingsPage,
  ThesisPage,
} from "./app/workspace-pages";
import "./styles.css";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: (failureCount, error) =>
        !(error instanceof Response && error.status < 500) && failureCount < 2,
    },
    mutations: { retry: false },
  },
});

const router = createBrowserRouter([
  {
    element: <RuntimeLayout />,
    children: [
      { path: "/", element: <DashboardPage /> },
      { path: "/portfolio", element: <PortfolioPage /> },
      { path: "/portfolio/import", element: <PortfolioImportPage /> },
      { path: "/positions/:positionId", element: <PositionDetailPage /> },
      { path: "/opportunities", element: <OpportunitiesPage /> },
      { path: "/review", element: <ReviewPage /> },
      { path: "/settings", element: <SettingsPage /> },
      { path: "/advanced/research", element: <AdvancedResearchPage /> },
      { path: "/advanced/data-health", element: <DataHealthPage /> },
      { path: "/advanced/backtests", element: <BacktestPage /> },
      { path: "/advanced/thesis", element: <ThesisPage /> },
      { path: "/advanced/journal", element: <JournalPage /> },
      { path: "/advanced/health", element: <HealthPage /> },
      { path: "/advanced/market-data", element: <MarketInspectionPage /> },
      { path: "/advanced/market-context", element: <MarketContextPage /> },
      { path: "/advanced/dip-buy", element: <DipPage /> },
      { path: "*", element: <DashboardPage /> },
    ],
  },
]);

const root = document.getElementById("root");
if (root === null) throw new Error("Missing #root application mount point");

createRoot(root).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </StrictMode>,
);
