import createClient from "openapi-fetch";
import type { paths } from "./schema.js";

export type { components, operations, paths } from "./schema.js";

export const api = createClient<paths>({ baseUrl: "/" });
