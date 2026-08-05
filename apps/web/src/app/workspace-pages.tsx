import { api } from "@portfolio/api-client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Activity, BookOpen, Settings } from "lucide-react";
import { ExecutiveDashboardPage } from "./dashboard/ExecutiveDashboardPage";
import { WorkspaceNav } from "./workspace-nav";

async function getSession() {
  const { data, response } = await api.GET("/api/v1/auth/session");
  if (!data)
    throw new Error(`Session unavailable (${String(response.status)})`);
  return data;
}
async function getWorkerHealth() {
  const { data, response } = await api.GET("/api/v1/worker/health");
  if (!data)
    throw new Error(`Worker health unavailable (${String(response.status)})`);
  return data;
}
async function getDataHealth() {
  const { data, response } = await api.GET("/api/v1/market/data-health");
  if (!data)
    throw new Error(`Data health unavailable (${String(response.status)})`);
  return data;
}

type CsrfToken = {
  headerName: string;
  parameterName: string;
  token: string;
};

async function submitSessionForm(
  path: "/api/v1/auth/login" | "/api/v1/auth/logout",
  fields: Record<string, string> = {},
) {
  const csrfResponse = await fetch("/api/v1/auth/csrf", {
    cache: "no-store",
    credentials: "same-origin",
  });
  if (!csrfResponse.ok) throw new Error("Unable to create a secure session.");

  const csrf = (await csrfResponse.json()) as CsrfToken;
  const body = new URLSearchParams(fields);
  body.set(csrf.parameterName, csrf.token);
  const response = await fetch(path, {
    method: "POST",
    credentials: "same-origin",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
      [csrf.headerName]: csrf.token,
    },
    body,
  });

  if (response.status === 401)
    throw new Error("The email or password is incorrect.");
  if (response.status === 429)
    throw new Error("Too many login attempts. Please wait and try again.");
  if (!response.ok)
    throw new Error(`Session request failed (${String(response.status)}).`);
}

function Frame({
  eyebrow,
  title,
  children,
}: React.PropsWithChildren<{ eyebrow: string; title: string }>) {
  return (
    <main className="shell workspace-shell">
      <WorkspaceNav />
      <section className="workspace-heading">
        <p className="eyebrow">{eyebrow}</p>
        <h1>{title}</h1>
      </section>
      {children}
      <footer>
        <span>DECISION SUPPORT ONLY</span>
        <span>ACKNOWLEDGEMENT IS NOT EXECUTION</span>
        <span>NO AUTO TRADING</span>
      </footer>
    </main>
  );
}

export function DashboardPage() {
  return <ExecutiveDashboardPage />;
}

export function PlanPage() {
  return (
    <Frame eyebrow="PRE-COMMITMENT CHECK" title="Trade plan">
      <section className="context-card concise-card">
        <Activity aria-hidden="true" />
        <h2>Preview before deciding</h2>
        <p>
          Classification, risk caps, cooling periods, evidence quality, and
          thesis state are checked before any quantity is shown.
        </p>
        <a href="/portfolio">Open portfolio plan builder →</a>
      </section>
    </Frame>
  );
}
export function ThesisPage() {
  return (
    <Frame eyebrow="CHANGE CONDITIONS" title="Thesis">
      <section className="context-card concise-card">
        <BookOpen aria-hidden="true" />
        <h2>Position-level evidence</h2>
        <p>
          Thesis confirmation, valuation revisions, earnings risk, and expiry
          live with each position—not in an unstructured watchlist.
        </p>
        <a href="/portfolio">Choose a position →</a>
      </section>
    </Frame>
  );
}
export function JournalPage() {
  return (
    <Frame eyebrow="AUDITABLE DECISIONS" title="Journal">
      <section className="context-card concise-card">
        <BookOpen aria-hidden="true" />
        <h2>Risk first</h2>
        <p>
          Entries record evidence, risk before reward, rule IDs, and what would
          change the decision. Page visits never create worker jobs.
        </p>
        <a href="/portfolio">Open position journals →</a>
      </section>
    </Frame>
  );
}

export function SettingsPage() {
  const queryClient = useQueryClient();
  const session = useQuery({ queryKey: ["session"], queryFn: getSession });
  const csrf = useQuery({
    queryKey: ["csrf"],
    queryFn: async () => {
      const { data, response } = await api.GET("/api/v1/auth/csrf");
      if (!data)
        throw new Error(`CSRF unavailable (${String(response.status)})`);
      return data;
    },
  });
  const sessionAction = useMutation({
    mutationFn: async (
      action:
        | { type: "login"; username: string; password: string }
        | { type: "logout" },
    ) => {
      if (action.type === "login") {
        await submitSessionForm("/api/v1/auth/login", {
          username: action.username,
          password: action.password,
        });
      } else {
        await submitSessionForm("/api/v1/auth/logout");
      }
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["session"] }),
        queryClient.invalidateQueries({ queryKey: ["actions-today"] }),
        csrf.refetch(),
      ]);
    },
  });
  const token = csrf.data;
  return (
    <Frame eyebrow="SESSION AND POLICY" title="Settings">
      <section className="context-card settings-card">
        <Settings aria-hidden="true" />
        <h2>
          {session.data?.authenticated ? "Authenticated session" : "Sign in"}
        </h2>
        <p>
          Authentication uses an HttpOnly same-origin session cookie. No access
          token is stored in browser storage.
        </p>
        {session.data?.authenticated ? (
          <form
            onSubmit={(event) => {
              event.preventDefault();
              sessionAction.mutate({ type: "logout" });
            }}
          >
            <button type="submit" disabled={sessionAction.isPending}>
              Sign out {session.data.username}
            </button>
          </form>
        ) : (
          <form
            className="login-form"
            onSubmit={(event) => {
              event.preventDefault();
              const fields = new FormData(event.currentTarget);
              const username = fields.get("username");
              const password = fields.get("password");
              sessionAction.mutate({
                type: "login",
                username: typeof username === "string" ? username : "",
                password: typeof password === "string" ? password : "",
              });
            }}
          >
            <label>
              Email
              <input
                required
                autoComplete="username"
                name="username"
                type="email"
              />
            </label>
            <label>
              Password
              <input
                required
                autoComplete="current-password"
                name="password"
                type="password"
              />
            </label>
            {token ? (
              <input
                type="hidden"
                name={token.parameterName}
                value={token.token}
              />
            ) : null}
            <button type="submit" disabled={!token || sessionAction.isPending}>
              {sessionAction.isPending ? "Signing in..." : "Sign in"}
            </button>
          </form>
        )}
        {sessionAction.isError ? (
          <p className="error" role="alert">
            {sessionAction.error.message}
          </p>
        ) : null}
      </section>
    </Frame>
  );
}

export function DataHealthPage() {
  const dataHealth = useQuery({
    queryKey: ["data-health"],
    queryFn: getDataHealth,
  });
  const worker = useQuery({
    queryKey: ["worker-health"],
    queryFn: getWorkerHealth,
  });
  return (
    <Frame eyebrow="OBSERVABILITY" title="Data health">
      <section className="dashboard-grid">
        <article className="context-card">
          <h2>Market data</h2>
          <strong className="large-status">
            {dataHealth.isPending
              ? "CHECKING"
              : dataHealth.isError
                ? "UNAVAILABLE"
                : dataHealth.data.status}
          </strong>
          <p>
            Freshness and completeness gates suppress precise recommendations
            when evidence is stale or missing.
          </p>
        </article>
        <article className="context-card">
          <h2>Durable worker</h2>
          <strong className="large-status">
            {worker.isPending
              ? "CHECKING"
              : worker.isError
                ? "PROTECTED"
                : `${String(worker.data.pendingJobs)} PENDING`}
          </strong>
          <p>
            {worker.data
              ? `${String(worker.data.deadJobs)} dead jobs require operator review.`
              : "Sign in to inspect the private job queue."}
          </p>
        </article>
      </section>
    </Frame>
  );
}
