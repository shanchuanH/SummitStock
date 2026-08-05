import { expect, test } from "@playwright/test";

test("renders the calm dashboard and navigates the workspace", async ({
  page,
}) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Dashboard" })).toBeVisible();
  await expect(page.getByText("NO AUTO TRADING")).toBeVisible();
  await page.getByRole("link", { name: "Settings" }).click();
  await expect(page.getByRole("heading", { name: "Settings" })).toBeVisible();
  await expect(page.getByText(/No access token is stored/i)).toBeVisible();
  await page.getByRole("link", { name: "Backtest" }).click();
  await expect(page.getByRole("heading", { name: "Backtest" })).toBeVisible();
  await expect(page.getByText("NO VERIFIED REPORT")).toBeVisible();
});
