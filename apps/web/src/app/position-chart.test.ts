import { describe, expect, it } from "vitest";
import {
  canonicalizeDatedValues,
  canonicalizeLinePoints,
} from "./position-chart-series";

describe("position chart series canonicalization", () => {
  it("sorts bars and keeps only the latest value for a market date", () => {
    const result = canonicalizeDatedValues([
      { marketDate: "2026-08-21", close: "118" },
      { marketDate: "2026-08-20", close: "117" },
      { marketDate: "2026-08-21", close: "119" },
    ]);

    expect(result).toEqual([
      { marketDate: "2026-08-20", close: "117" },
      { marketDate: "2026-08-21", close: "119" },
    ]);
  });

  it("sorts and deduplicates stop and indicator line points", () => {
    expect(
      canonicalizeLinePoints([
        { time: "2026-08-21", value: 118 },
        { time: "2026-08-20", value: 117 },
        { time: "2026-08-21", value: 119 },
      ]),
    ).toEqual([
      { time: "2026-08-20", value: 117 },
      { time: "2026-08-21", value: 119 },
    ]);
  });
});
