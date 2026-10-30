import { describe, it, expect } from 'vitest';
import { computeSha256, computeBlobSha256 } from './crypto';

describe('Crypto utils', () => {
  it('computes correct SHA-256 for string buffer', async () => {
    const encoder = new TextEncoder();
    const data = encoder.encode('hello');
    const hash = await computeSha256(data.buffer);
    expect(hash).toBe('2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824');
  });

  it('computes correct SHA-256 for empty buffer', async () => {
    const buffer = new ArrayBuffer(0);
    const hash = await computeSha256(buffer);
    expect(hash).toBe('e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855');
  });

  it('computes correct SHA-256 for Blob', async () => {
    const blob = new Blob(['hello world']);
    const hash = await computeBlobSha256(blob);
    // sha256 of "hello world"
    expect(hash).toBe('b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9');
  });
});
