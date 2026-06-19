/** App-level flat backdrop (Instagram-style solid surface). */
export function AmbientBackground() {
  return (
    <div className="absolute inset-0 z-0 pointer-events-none bg-ig-bg dark:bg-ig-bg-dark" />
  );
}
