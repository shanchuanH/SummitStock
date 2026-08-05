import {
  CandlestickSeries,
  ColorType,
  LineSeries,
  createChart,
  createSeriesMarkers,
  type SeriesMarker,
  type Time,
} from "lightweight-charts";
import { useEffect, useRef } from "react";

export type PositionChartData = {
  bars: {
    marketDate: string;
    open: string;
    high: string;
    low: string;
    close: string;
    quality: string;
  }[];
  entryMarkers: { marketDate: string; price: string; markerType: string }[];
  stopSeries: {
    marketDate: string;
    formalStop?: string | null;
    liveStop?: string | null;
    softAlert?: string | null;
  }[];
  earningsMarkers: { marketDate: string; markerType: string; label: string }[];
  tradeMarkers: { marketDate: string; markerType: string; label: string }[];
  dataAsOf?: string | null;
  quality: string;
};

export function PositionChart({ data }: { data: PositionChartData }) {
  const host = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!host.current || !data.bars.length) return;
    const chart = createChart(host.current, {
      height: 390,
      width: host.current.clientWidth,
      layout: {
        background: { type: ColorType.Solid, color: "#101718" },
        textColor: "#b8c6c1",
      },
      grid: {
        vertLines: { color: "#233032" },
        horzLines: { color: "#233032" },
      },
      rightPriceScale: { borderColor: "#334344" },
      timeScale: { borderColor: "#334344", timeVisible: true },
    });
    const candles = chart.addSeries(CandlestickSeries, {
      upColor: "#4bd2a0",
      downColor: "#ed6a5a",
      wickUpColor: "#4bd2a0",
      wickDownColor: "#ed6a5a",
      borderVisible: false,
    });
    candles.setData(
      data.bars.map((bar) => ({
        time: bar.marketDate,
        open: Number(bar.open),
        high: Number(bar.high),
        low: Number(bar.low),
        close: Number(bar.close),
      })),
    );
    const entry = chart.addSeries(LineSeries, {
      color: "#f6c85f",
      lineWidth: 2,
      title: "平均成本",
    });
    const entryPrice = data.entryMarkers.at(-1)?.price;
    if (entryPrice)
      entry.setData(
        data.bars.map((bar) => ({
          time: bar.marketDate,
          value: Number(entryPrice),
        })),
      );
    const formal = chart.addSeries(LineSeries, {
      color: "#ed6a5a",
      lineWidth: 2,
      title: "收盘确认风险线",
    });
    formal.setData(
      data.stopSeries
        .filter((point) => point.formalStop != null)
        .map((point) => ({
          time: point.marketDate,
          value: Number(point.formalStop),
        })),
    );
    const soft = chart.addSeries(LineSeries, {
      color: "#f39c6b",
      lineWidth: 1,
      lineStyle: 2,
      title: "风险提醒线",
    });
    soft.setData(
      data.stopSeries
        .filter((point) => point.softAlert != null)
        .map((point) => ({
          time: point.marketDate,
          value: Number(point.softAlert),
        })),
    );
    const markers: SeriesMarker<Time>[] = [
      ...data.earningsMarkers.map((item) => ({
        time: item.marketDate,
        position: "aboveBar" as const,
        color: "#b399ff",
        shape: "circle" as const,
        text: item.label,
      })),
      ...data.tradeMarkers.map((item) => ({
        time: item.marketDate,
        position: "belowBar" as const,
        color: "#75bfff",
        shape: "arrowUp" as const,
        text: item.label,
      })),
      ...data.entryMarkers.map((item) => ({
        time: item.marketDate,
        position: "atPriceMiddle" as const,
        price: Number(item.price),
        color: "#f6c85f",
        shape: "square" as const,
        text: "平均成本",
      })),
    ].sort((a, b) => a.time.localeCompare(b.time));
    createSeriesMarkers(candles, markers);
    chart.timeScale().fitContent();
    const resize =
      typeof ResizeObserver === "undefined"
        ? undefined
        : new ResizeObserver(() =>
            { chart.applyOptions({ width: host.current?.clientWidth ?? 0 }); },
          );
    resize?.observe(host.current);
    return () => {
      resize?.disconnect();
      chart.remove();
    };
  }, [data]);
  if (!data.bars.length)
    return (
      <div className="chart-empty">
        <strong>暂无可用价格图表</strong>
        <span>缺少真实日线数据，系统不会绘制模拟走势。</span>
      </div>
    );
  return (
    <>
      <div
        className="real-position-chart"
        ref={host}
        aria-label="持仓价格、平均成本、风险线与事件图表"
      />
      <p className="chart-caption">
        图表质量：{data.quality} · 数据截至{" "}
        {data.dataAsOf
          ? new Date(data.dataAsOf).toLocaleString("zh-CN")
          : "待确认"}
      </p>
    </>
  );
}
