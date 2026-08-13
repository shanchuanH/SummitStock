type CsrfToken = { headerName: string; token: string };
type Problem = {
  detail?: string;
  code?: string;
  nextAction?: string;
  requestId?: string;
};

export class OwnerApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    readonly nextAction: string,
    readonly requestId?: string,
  ) {
    super(nextAction);
  }
}

async function ownerError(response: Response) {
  let problem: Problem = {};
  try {
    problem = (await response.json()) as Problem;
  } catch {
    /* A non-JSON proxy failure still receives safe owner text. */
  }
  const nextAction =
    problem.nextAction ??
    (response.status >= 500
      ? "服务暂时不可用，请稍后重试；不要依据不完整分析操作。"
      : "请检查输入或返回上一页重试。");
  return new OwnerApiError(
    response.status,
    problem.code ?? "REQUEST_FAILED",
    nextAction,
    problem.requestId,
  );
}

export async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(path, {
    cache: "no-store",
    credentials: "same-origin",
  });
  if (!response.ok) throw await ownerError(response);
  return response.json() as Promise<T>;
}

export async function postJson<T>(path: string, body: object): Promise<T> {
  const tokenResponse = await fetch("/api/v1/auth/csrf", {
    cache: "no-store",
    credentials: "same-origin",
  });
  if (!tokenResponse.ok) throw new Error("无法建立安全会话。");
  const csrf = (await tokenResponse.json()) as CsrfToken;
  const response = await fetch(path, {
    method: "POST",
    credentials: "same-origin",
    headers: {
      "Content-Type": "application/json",
      [csrf.headerName]: csrf.token,
    },
    body: JSON.stringify(body),
  });
  if (!response.ok) throw await ownerError(response);
  return response.json() as Promise<T>;
}
