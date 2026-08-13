import type { ImportPreview } from "./types";

export function ImportConfirmationStep({
  preview,
  ready,
  busy,
  onConfirm,
  onReset,
}: {
  preview: ImportPreview;
  ready: boolean;
  busy: boolean;
  onConfirm: () => void;
  onReset: () => void;
}) {
  return (
    <section className="context-card import-confirm-card">
      <p className="eyebrow">第 5 步 / 确认并分析</p>
      <h2>确认这是账户的完整快照</h2>
      <p>
        匹配的持仓会更新，历史快照会保留；本次完整账户快照中已不存在的持仓会被关闭。
      </p>
      {!ready ? (
        <p className="import-warning" role="alert">
          请先修正每一行错误、确认全部分类和生活备用金位置。
        </p>
      ) : null}
      <div className="import-actions">
        <button disabled={!ready || busy} onClick={onConfirm} type="button">
          {busy ? "正在确认…" : "确认并开始分析"}
        </button>
        <button disabled={busy} onClick={onReset} type="button">
          重新开始
        </button>
      </div>
      <small>
        Batch {preview.batchId} · version {preview.version}
      </small>
    </section>
  );
}
