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
import {
  canonicalizeDatedValues,
  canonicalizeLinePoints,
} from "./position-chart-series";

export type PositionChartData = {
  bars: {
    marketDate: string;
    open: string;
    high: string;
    low: string;
    close: string;
    quality: string;
  }[];
  entryMarkers: {
    marketDate: string;
    price: string;
    markerType: string;
  }[];
  stopSeries: {
    marketDate: string;
    formalStop?: string | null;
    liveStop?: string | null;
    softAlert?: string | null;
  }[];
  earningsMarkers: {
    marketDate: string;
    markerType: string;
    label: string;
  }[];
  tradeMarkers: {
    marketDate: string;
    markerType: string;
    label: string;
  }[];
  dataAsOf?: string | null;
  quality: string;
};

function movingAverage(bars: PositionChartData["bars"], window: number) {
  return bars
    .map((bar, index) => ({ bar, index }))
    .filter(({ index }) => index >= window - 1)
    .map(({ bar, index }) => ({
      time: bar.marketDate,
      value:
        bars
          .slice(index - window + 1, index + 1)
          .reduce((sum, item) => sum + Number(item.close), 0) / window,
    }));
}

function exponentialMovingAverage(
  bars: PositionChartData["bars"],
  window: number,
) {
  if (bars.length < window) return [];
  const multiplier = 2 / (window + 1);
  let average =
    bars.slice(0, window).reduce((sum, item) => sum + Number(item.close), 0) /
    window;
  return bars.slice(window - 1).map((bar, index) => {
    if (index > 0) {
      average = Number(bar.close) * multiplier + average * (1 - multiplier);
    }
    return { time: bar.marketDate, value: average };
  });
}

export function PositionChart({ data }: { data: PositionChartData }) {
  const host = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!host.current || !data.bars.length) return;
    const css = getComputedStyle(document.documentElement);
    const color = (name: string) => css.getPropertyValue(name).trim();
    const chart = createChart(host.current, {
      height: 390,
      width: host.current.clientWidth,
      layout: {
        background: { type: ColorType.Solid, color: color("--surface-1") },
        textColor: color("--muted"),
      },
      grid: {
        vertLines: { color: color("--border") },
        horzLines: { color: color("--border") },
      },
      rightPriceScale: { borderColor: color("--border") },
      timeScale: { borderColor: color("--border"), timeVisible: true },
    });
    const candles = chart.addSeries(CandlestickSeries, {
      upColor: color("--green"),
      downColor: color("--red"),
      wickUpColor: color("--green"),
      wickDownColor: color("--red"),
      borderVisible: false,
    });
    const bars = canonicalizeDatedValues(data.bars);
    candles.setData(
      bars.map((bar) => ({
        time: bar.marketDate,
        open: Number(bar.open),
        high: Number(bar.high),
        low: Number(bar.low),
        close: Number(bar.close),
      })),
    );
    const addLine = (
      title: string,
      lineColor: string,
      points: { time: string; value: number }[],
      lineStyle = 0,
    ) => {
      const series = chart.addSeries(LineSeries, {
        color: lineColor,
        lineWidth: 2,
        title,
        lineStyle,
      });
      series.setData(canonicalizeLinePoints(points));
    };
    addLine(
      "EMA20",
      color("--purple"),
      exponentialMovingAverage(bars, 20),
    );
    addLine("SMA50", color("--blue"), movingAverage(bars, 50));
    addLine(
      "正式止损",
      color("--red"),
      data.stopSeries
        .filter((item) => item.formalStop != null)
        .map((item) => ({
          time: item.marketDate,
          value: Number(item.formalStop),
        })),
    );
    addLine(
      "动态止损",
      color("--amber"),
      data.stopSeries
        .filter((item) => item.liveStop != null)
        .map((item) => ({
          time: item.marketDate,
          value: Number(item.liveStop),
        })),
      2,
    );
    const markers: SeriesMarker<Time>[] = data.earningsMarkers
      .map((item) => ({
        time: item.marketDate,
        position: "aboveBar" as const,
        color: color("--purple"),
        shape: "circle" as const,
        text: item.label,
      }))
      .sort((a, b) => a.time.localeCompare(b.time));
    createSeriesMarkers(candles, markers);
    chart.timeScale().fitContent();
    const resize =
      typeof ResizeObserver === "undefined"
        ? undefined
        : new ResizeObserver(() => {
            chart.applyOptions({ width: host.current?.clientWidth ?? 0 });
          });
    resize?.observe(host.current);
    return () => {
      resize?.disconnect();
      chart.remove();
    };
  }, [data]);

  if (!data.bars.length) {
    return (
      <div className="chart-empty">
        <strong>暂无可用价格图表</strong>
        <span>缺少真实日线数据，系统不会绘制模拟走势。</span>
      </div>
    );
  }
  return (
    <>
      <div
        className="real-position-chart"
        ref={host}
        aria-label="持仓价格、EMA20、SMA50、正式止损、动态止损与财报事件图表"
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
