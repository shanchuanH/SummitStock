import { spawnSync } from "node:child_process";

const executable = process.platform === "win32" ? "mvnw.cmd" : "./mvnw";
const result = spawnSync(
  executable,
  [
    "-pl",
    "apps/backend",
    "-am",
    "-Dtest=ApiContractTest",
    "-Dsurefire.failIfNoSpecifiedTests=false",
    "test",
  ],
  {
    cwd: new URL("..", import.meta.url),
    stdio: "inherit",
    shell: process.platform === "win32",
  },
);

if (result.status !== 0) process.exit(result.status ?? 1);
