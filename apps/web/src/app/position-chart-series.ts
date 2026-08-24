export function canonicalizeDatedValues<T extends { marketDate: string }>(
  values: T[],
): T[] {
  const latestByDate = new Map<string, T>();
  values.forEach((value) => latestByDate.set(value.marketDate, value));
  return [...latestByDate.values()].sort((left, right) =>
    left.marketDate.localeCompare(right.marketDate),
  );
}

export function canonicalizeLinePoints(
  points: { time: string; value: number }[],
) {
  const latestByTime = new Map<string, { time: string; value: number }>();
  points.forEach((point) => latestByTime.set(point.time, point));
  return [...latestByTime.values()].sort((left, right) =>
    left.time.localeCompare(right.time),
  );
}
