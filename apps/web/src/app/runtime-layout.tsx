import { Outlet } from "react-router";
import { StrategyStatusBanner } from "./strategy-status-banner";

export function RuntimeLayout() {
  return (
    <>
      <StrategyStatusBanner />
      <Outlet />
    </>
  );
}
