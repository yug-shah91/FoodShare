// Pure React SVG QR code voucher graphic with authentic styling and finder patterns
export default function QrCode({ value, size = 140 }) {
  // Generate deterministic dot pattern based on string characters
  const grid = 21; // 21x21 QR matrix size
  const dots = [];

  // Seeded pseudo-random bit pattern from the value string
  let seed = 0;
  for (let i = 0; i < value.length; i++) {
    seed = (seed * 31 + value.charCodeAt(i)) % 100000;
  }

  function isFinderPattern(r, c) {
    // Top-left
    if (r < 7 && c < 7) return true;
    // Top-right
    if (r < 7 && c >= grid - 7) return true;
    // Bottom-left
    if (r >= grid - 7 && c < 7) return true;
    return false;
  }

  function getFinderPixel(r, c) {
    const checkSub = (row, col) => {
      if (row === 0 || row === 6 || col === 0 || col === 6) return true;
      if (row >= 2 && row <= 4 && col >= 2 && col <= 4) return true;
      return false;
    };
    if (r < 7 && c < 7) return checkSub(r, c);
    if (r < 7 && c >= grid - 7) return checkSub(r, c - (grid - 7));
    if (r >= grid - 7 && c < 7) return checkSub(r - (grid - 7), c);
    return false;
  }

  for (let r = 0; r < grid; r++) {
    for (let c = 0; c < grid; c++) {
      if (isFinderPattern(r, c)) {
        if (getFinderPixel(r, c)) {
          dots.push({ r, c });
        }
      } else {
        // Pseudo-random data bit
        seed = (seed * 1103515245 + 12345) & 0x7fffffff;
        if (seed % 3 === 0 || (r + c) % 2 === 0) {
          dots.push({ r, c });
        }
      }
    }
  }

  const cellSize = size / grid;

  return (
    <div className="qr-container" style={{ width: size, height: size }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
        <rect width={size} height={size} fill="#ffffff" rx="8" />
        {dots.map((d, i) => (
          <rect
            key={i}
            x={d.c * cellSize}
            y={d.r * cellSize}
            width={cellSize * 0.92}
            height={cellSize * 0.92}
            rx={cellSize * 0.25}
            fill="#114b26"
          />
        ))}
      </svg>
    </div>
  );
}
