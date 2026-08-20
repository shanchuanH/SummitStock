import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { actionPresentations, presentAction } from "./action-presentation";

describe("action presentation", () => {
  it("covers every deterministic backend recommendation action", () => {
    const source = readFileSync(
      resolve(
        process.cwd(),
        "../backend/src/main/java/com/example/portfolio/analysis/domain/RecommendationAction.java",
      ),
      "utf8",
    );
    const body = source.match(
      /enum RecommendationAction\s*\{([\s\S]*?)\}/,
    )?.[1];
    const backendActions = body?.match(/[A-Z][A-Z_]+/g) ?? [];
    expect(backendActions.length).toBeGreaterThan(0);
    expect(
      backendActions.filter((action) => !(action in actionPresentations)),
    ).toEqual([]);
  });

  it("never exposes an unknown raw enum as owner-facing copy", () => {
    expect(presentAction("NEW_BACKEND_ACTION").title).toBe(
      "建议状态尚未识别，暂不操作",
    );
    expect(presentAction("DO_NOT_ADD").title).not.toBe("DO_NOT_ADD");
    expect(presentAction("SELL").tone).toBe("danger");
  });
});
