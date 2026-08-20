import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { getJson, postJson } from "./http";
import { presentClassification } from "./presentation/classification-presentation";

type Suggestion = {
  positionId: string;
  symbol: string;
  assetType: string;
  classification: string;
  source: string;
  blocked: boolean;
  reason: string;
  confirmationRequired: boolean;
};

const classifications = [
  "CORE_BROAD_ETF",
  "CORE_TECH_ETF",
  "QUALITY_STOCK",
  "QUALITY_GROWTH_HIGH_VOL",
  "THEMATIC_ETF",
  "TACTICAL_STOCK",
  "CYCLICAL_TACTICAL",
  "TURNAROUND_TACTICAL",
  "SPECULATIVE",
  "CASH_EQUIVALENT",
  "UNVESTED_COMPENSATION",
];

export function ClassificationModal({
  positionId,
  version,
  onClose,
}: {
  positionId: string;
  version: number;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const suggestion = useQuery({
    queryKey: ["classification-suggestion", positionId],
    queryFn: () =>
      getJson<Suggestion>(
        `/api/v1/positions/${positionId}/classification-suggestion`,
      ),
  });
  const [selected, setSelected] = useState("");
  useEffect(() => {
    if (suggestion.data) setSelected(suggestion.data.classification);
  }, [suggestion.data]);
  const confirm = useMutation({
    mutationFn: () =>
      postJson(`/api/v1/positions/${positionId}/classify`, {
        classification: selected,
        expectedVersion: version,
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["portfolio-holdings"] });
      onClose();
    },
  });
  return (
    <div
      className="modal-backdrop"
      role="presentation"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <section
        className="classification-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="classification-title"
      >
        <button className="modal-close" aria-label="关闭" onClick={onClose}>
          ×
        </button>
        <p className="eyebrow">持仓分类确认</p>
        <h2 id="classification-title">
          {suggestion.data?.symbol ?? "加载中"} 的系统建议
        </h2>
        {suggestion.isPending ? (
          <p>正在读取证据…</p>
        ) : suggestion.isError ? (
          <p role="alert">无法读取分类建议。</p>
        ) : (
          <>
            <dl className="classification-evidence">
              <div>
                <dt>系统建议</dt>
                <dd>{presentClassification(suggestion.data.classification)}</dd>
              </div>
              <div>
                <dt>依据</dt>
                <dd>{suggestion.data.reason}</dd>
              </div>
              <div>
                <dt>资产类型</dt>
                <dd>{suggestion.data.assetType}</dd>
              </div>
              <div>
                <dt>限制</dt>
                <dd>
                  {suggestion.data.blocked
                    ? "现有证据阻止自动采用，需要人工判断。"
                    : "系统不会自动修改分类，必须由你确认。"}
                </dd>
              </div>
            </dl>
            <label>
              确认或修改为其他分类
              <select
                value={selected}
                onChange={(event) => {
                  setSelected(event.target.value);
                }}
              >
                {classifications.map((item) => (
                  <option key={item} value={item}>
                    {presentClassification(item)}
                  </option>
                ))}
              </select>
            </label>
            <div className="modal-actions">
              <button onClick={onClose}>取消</button>
              <button
                disabled={!selected || confirm.isPending}
                onClick={() => {
                  confirm.mutate();
                }}
              >
                {confirm.isPending ? "正在确认…" : "确认分类"}
              </button>
            </div>
            {confirm.isError ? (
              <p role="alert">分类提交失败，持仓可能已被其他操作更新。</p>
            ) : null}
          </>
        )}
      </section>
    </div>
  );
}
