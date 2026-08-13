import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { AdvancedResearchPage } from "./workspace-pages";

describe("AdvancedResearchPage", () => {
  it("keeps all five advanced tools behind one secondary hub", () => {
    render(<AdvancedResearchPage />);
    expect(screen.getByRole("link", { name: /Regime/ })).toHaveAttribute(
      "href",
      "/advanced/market-context",
    );
    expect(screen.getByRole("link", { name: /Backtest/ })).toHaveAttribute(
      "href",
      "/advanced/backtests",
    );
    expect(screen.getByRole("link", { name: /Thesis/ })).toHaveAttribute(
      "href",
      "/advanced/thesis",
    );
    expect(screen.getByRole("link", { name: /Journal/ })).toHaveAttribute(
      "href",
      "/advanced/journal",
    );
    expect(screen.getByRole("link", { name: /Data Health/ })).toHaveAttribute(
      "href",
      "/advanced/data-health",
    );
  });
});
