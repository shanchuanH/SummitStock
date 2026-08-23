import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CashSetupStep } from "./CashSetupStep";

describe("CashSetupStep", () => {
  afterEach(cleanup);

  it("collects split Fidelity and external emergency cash independently", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const onNext = vi.fn();
    const { rerender } = render(
      <CashSetupStep
        emergencyTarget="20000"
        importedCash="30000"
        onBack={vi.fn()}
        onChange={onChange}
        onNext={onNext}
        value={undefined}
      />,
    );

    await user.click(
      screen.getByRole("radio", { name: "Fidelity 和外部银行中都有" }),
    );
    expect(onChange).toHaveBeenLastCalledWith({
      location: "SPLIT",
      fidelityAmount: "",
      externalAmount: "",
    });

    rerender(
      <CashSetupStep
        emergencyTarget="20000"
        importedCash="30000"
        onBack={vi.fn()}
        onChange={onChange}
        onNext={onNext}
        value={{
          location: "SPLIT",
          fidelityAmount: "10000",
          externalAmount: "10000",
        }}
      />,
    );

    expect(screen.getByText(/^备用金合计/)).toHaveTextContent("US$20,000");
    expect(screen.getByText(/^距离目标/)).toHaveTextContent("US$0");
    expect(screen.getByText(/^Fidelity 剩余可投资现金/)).toHaveTextContent(
      "US$20,000",
    );
    expect(screen.getByRole("button", { name: "继续最终确认" })).toBeEnabled();
  });

  it("rejects a Fidelity split amount above imported broker cash", () => {
    render(
      <CashSetupStep
        emergencyTarget="20000"
        importedCash="15000"
        onBack={vi.fn()}
        onChange={vi.fn()}
        onNext={vi.fn()}
        value={{
          location: "SPLIT",
          fidelityAmount: "20000",
          externalAmount: "5000",
        }}
      />,
    );

    expect(screen.getByRole("alert")).toHaveTextContent(
      "不能超过本次文件识别到的 Fidelity 现金",
    );
    expect(screen.getByRole("button", { name: "继续最终确认" })).toBeDisabled();
  });
});
