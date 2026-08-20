import { api } from "@portfolio/api-client";
import { useQuery } from "@tanstack/react-query";

export function StrategyStatusBanner() {
  const version = useQuery({
    queryKey: ["api-version"],
    queryFn: async () => {
      const result = await api.GET("/api/v1/version");
      if (result.data === undefined)
        throw new Error("Strategy status unavailable");
      return result.data;
    },
    staleTime: 60_000,
    retry: false,
  });

  if (version.isPending || version.isError) return null;
  const fixtureData = (version.data as { fixtureData?: boolean }).fixtureData;
  if (fixtureData) {
    return (
      <aside className="strategy-status-banner" role="alert">
        <strong>DEMO / FIXTURE DATA</strong>
        <span>当前页面使用确定性演示数据，不代表真实市场或正式投资建议。</span>
      </aside>
    );
  }
  if (version.data.productionStrategy) return null;

  const draftOverride = version.data.draftStrategyOverride;
  return (
    <aside className="strategy-status-banner" role="alert">
      <strong>
        {draftOverride
          ? "DRAFT STRATEGY / NOT PRODUCTION"
          : "STRATEGY BLOCKED / NOT PRODUCTION"}
      </strong>
      <span>
        {version.data.strategyVersion} / DB status{" "}
        {version.data.strategyPublishState}
        {draftOverride
          ? " / formal recommendations are enabled only by the explicit draft override"
          : " / formal ACTIVE recommendations are disabled"}
      </span>
    </aside>
  );
}
