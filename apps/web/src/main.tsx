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
import {
  DashboardPage,
  DataHealthPage,
  JournalPage,
  PlanPage,
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
  { path: "/", element: <DashboardPage /> },
  { path: "/foundation", element: <HealthPage /> },
  { path: "/market-data", element: <MarketInspectionPage /> },
  { path: "/market-context", element: <MarketContextPage /> },
  { path: "/portfolio", element: <PortfolioPage /> },
  { path: "/positions/:positionId", element: <PositionDetailPage /> },
  { path: "/dip-buy", element: <DipPage /> },
  { path: "/plan", element: <PlanPage /> },
  { path: "/thesis", element: <ThesisPage /> },
  { path: "/journal", element: <JournalPage /> },
  { path: "/settings", element: <SettingsPage /> },
  { path: "/data-health", element: <DataHealthPage /> },
  { path: "/backtests", element: <BacktestPage /> },
  { path: "*", element: <DashboardPage /> },
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
