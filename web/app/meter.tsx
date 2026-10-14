export function ProgressMeter({ completed, total }: { completed: number; total: number }) {
  const width = total <= 0 ? 0 : Math.min(100, (completed / total) * 100);

  return (
    <span className="meter" aria-hidden="true">
      <span style={{ width: `${width}%` }} />
    </span>
  );
}
