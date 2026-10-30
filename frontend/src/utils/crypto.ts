/**
 * Helper to compute SHA-256 hex checksum for an ArrayBuffer or Blob in modern browsers.
 */
export async function computeSha256(data: ArrayBuffer): Promise<string> {
  if (window.crypto && window.crypto.subtle) {
    const hashBuffer = await window.crypto.subtle.digest('SHA-256', data);
    const hashArray = Array.from(new Uint8Array(hashBuffer));
    return hashArray.map((b) => b.toString(16).padStart(2, '0')).join('');
  }
  // Fallback if subtle crypto is not available (e.g. non-secure context in old test runner)
  return '';
}

export async function computeBlobSha256(blob: Blob): Promise<string> {
  const buffer = await blob.arrayBuffer();
  return computeSha256(buffer);
}
