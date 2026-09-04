const makeTable = (): Int32Array => {
  const table = new Int32Array(256)
  for (let i = 0; i < 256; i++) {
    let c = i
    for (let j = 0; j < 8; j++) {
      c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
    }
    table[i] = c
  }
  return table
}

const crcTable = makeTable()

/**
 * Computes an IEEE 802.3 CRC32 from a string or byte array.
 * Result is an unsigned 32-bit integer (0..0xFFFFFFFF), matching
 * `java.util.zip.CRC32` over UTF-8 bytes.
 */
export function calculateCrc32(input: string | Uint8Array | null | undefined): number {
  if (input == null) {
    return 0
  }
  const bytes = typeof input === 'string' ? new TextEncoder().encode(input) : input
  let crc = 0 ^ -1
  for (let i = 0; i < bytes.length; i++) {
    crc = (crc >>> 8) ^ crcTable[(crc ^ bytes[i]) & 0xff]
  }
  return (crc ^ -1) >>> 0
}

/** Formats an unsigned CRC32 number as an 8-digit lower-case hex string. */
export function formatCrc32(hash: number): string {
  return (hash >>> 0).toString(16).padStart(8, '0')
}

export function useCrc32() {
  return { calculateCrc32, formatCrc32 }
}
