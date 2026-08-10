type CsrfToken = { headerName: string; token: string };

export async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(path, {
    cache: "no-store",
    credentials: "same-origin",
  });
  if (!response.ok)
    throw new Error(`API request failed (${String(response.status)}).`);
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
  if (!response.ok) throw new Error(`提交失败（${String(response.status)}）。`);
  return response.json() as Promise<T>;
}
