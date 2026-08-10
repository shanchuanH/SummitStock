import { CandlestickSeries, ColorType, LineSeries, createChart, createSeriesMarkers, type SeriesMarker, type Time } from "lightweight-charts";
import { useEffect, useRef } from "react";

export type PositionChartData={bars:{marketDate:string;open:string;high:string;low:string;close:string;quality:string}[];entryMarkers:{marketDate:string;price:string;markerType:string}[];stopSeries:{marketDate:string;formalStop?:string|null;liveStop?:string|null;softAlert?:string|null}[];earningsMarkers:{marketDate:string;markerType:string;label:string}[];tradeMarkers:{marketDate:string;markerType:string;label:string}[];dataAsOf?:string|null;quality:string};
function movingAverage(bars:PositionChartData["bars"],window:number){return bars.map((bar,index)=>({bar,index})).filter(({index})=>index>=window-1).map(({bar,index})=>({time:bar.marketDate,value:bars.slice(index-window+1,index+1).reduce((sum,x)=>sum+Number(x.close),0)/window}));}
export function PositionChart({data}:{data:PositionChartData}){
  const host=useRef<HTMLDivElement>(null);
  useEffect(()=>{
    if(!host.current||!data.bars.length)return;
    const css=getComputedStyle(document.documentElement);const color=(name:string)=>css.getPropertyValue(name).trim();
    const chart=createChart(host.current,{height:390,width:host.current.clientWidth,layout:{background:{type:ColorType.Solid,color:color("--surface-1")},textColor:color("--muted")},grid:{vertLines:{color:color("--border")},horzLines:{color:color("--border")}},rightPriceScale:{borderColor:color("--border")},timeScale:{borderColor:color("--border"),timeVisible:true}});
    const candles=chart.addSeries(CandlestickSeries,{upColor:color("--green"),downColor:color("--red"),wickUpColor:color("--green"),wickDownColor:color("--red"),borderVisible:false});
    candles.setData(data.bars.map(bar=>({time:bar.marketDate,open:Number(bar.open),high:Number(bar.high),low:Number(bar.low),close:Number(bar.close)})));
    const addLine=(title:string,lineColor:string,points:{time:string;value:number}[],lineStyle=0)=>{const series=chart.addSeries(LineSeries,{color:lineColor,lineWidth:2,title,lineStyle});series.setData(points);};
    addLine("SMA50",color("--blue"),movingAverage(data.bars,50));addLine("SMA200",color("--purple"),movingAverage(data.bars,200));
    const entryPrice=data.entryMarkers.at(-1)?.price;if(entryPrice)addLine("平均成本",color("--amber"),data.bars.map(bar=>({time:bar.marketDate,value:Number(entryPrice)})));
    addLine("正式止损",color("--red"),data.stopSeries.filter(x=>x.formalStop!=null||x.liveStop!=null).map(x=>({time:x.marketDate,value:Number(x.liveStop??x.formalStop)})));
    addLine("软提醒",color("--amber"),data.stopSeries.filter(x=>x.softAlert!=null).map(x=>({time:x.marketDate,value:Number(x.softAlert)})),2);
    const markers:SeriesMarker<Time>[]=[...data.earningsMarkers.map(x=>({time:x.marketDate,position:"aboveBar" as const,color:color("--purple"),shape:"circle" as const,text:x.label})),...data.tradeMarkers.map(x=>({time:x.marketDate,position:"belowBar" as const,color:color("--blue"),shape:"arrowUp" as const,text:x.label})),...data.entryMarkers.map(x=>({time:x.marketDate,position:"atPriceMiddle" as const,price:Number(x.price),color:color("--amber"),shape:"square" as const,text:"平均成本"}))].sort((a,b)=>a.time.localeCompare(b.time));
    createSeriesMarkers(candles,markers);chart.timeScale().fitContent();
    const resize=typeof ResizeObserver==="undefined"?undefined:new ResizeObserver(()=>{chart.applyOptions({width:host.current?.clientWidth??0});});resize?.observe(host.current);
    return()=>{resize?.disconnect();chart.remove();};
  },[data]);
  if(!data.bars.length)return <div className="chart-empty"><strong>暂无可用价格图表</strong><span>缺少真实日线数据，系统不会绘制模拟走势。</span></div>;
  return <><div className="real-position-chart" ref={host} aria-label="持仓价格、均线、平均成本、风险线与事件图表"/><p className="chart-caption">图表质量：{data.quality} · 数据截至 {data.dataAsOf?new Date(data.dataAsOf).toLocaleString("zh-CN"):"待确认"}</p></>;
}
