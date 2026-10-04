/** Bounded JPEG header validation, never a full-frame decode or whole-photo buffer. */
export function jpegHeader(bytes: Uint8Array): boolean {
  if (bytes[0] !== 255 || bytes[1] !== 216) return false;
  let at = 2;
  let frame = false;
  while (at + 4 <= bytes.length) {
    if (bytes[at++] !== 255) return false;
    while (bytes[at] === 255) at++;
    const marker = bytes[at++];
    if (marker === 0 || marker === 216 || marker === 217) return false;
    const length = (bytes[at] << 8) | bytes[at + 1];
    if (length < 2 || at + length > bytes.length) return false;
    if ([192, 193, 194].includes(marker)) {
      if (
        length < 8 || bytes[at + 2] !== 8 ||
        ((bytes[at + 3] << 8) | bytes[at + 4]) === 0 ||
        ((bytes[at + 5] << 8) | bytes[at + 6]) === 0
      ) return false;
      frame = true;
    }
    if (marker === 218) return frame && length >= 6;
    at += length;
  }
  return false;
}
