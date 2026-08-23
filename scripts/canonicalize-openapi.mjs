import { readFile, writeFile } from "node:fs/promises";

const contract = new URL("../contracts/openapi/portfolio-api.json", import.meta.url);

function canonical(value) {
  if (Array.isArray(value)) return value.map(canonical);
  if (value && typeof value === "object") {
    return Object.fromEntries(
      Object.entries(value)
        .sort(([left], [right]) => left.localeCompare(right))
        .map(([key, entry]) => [key, canonical(entry)]),
    );
  }
  return value;
}

const document = JSON.parse(await readFile(contract, "utf8"));
await writeFile(contract, `${JSON.stringify(canonical(document), null, 2)}\n`, "utf8");
