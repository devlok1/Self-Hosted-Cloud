import { describe, it, expect } from 'vitest'
import { formatBytes, formatDate, getFileCategory } from './formatters.ts'

describe('formatBytes', () => {
  it('formats 0 and empty bytes correctly', () => {
    expect(formatBytes(0)).toBe('0 B')
    expect(formatBytes(undefined)).toBe('0 B')
    expect(formatBytes(null)).toBe('0 B')
    expect(formatBytes(-10)).toBe('0 B')
  })

  it('formats bytes, kilobytes, megabytes, gigabytes accurately', () => {
    expect(formatBytes(512, 0)).toBe('512 B')
    expect(formatBytes(1024, 2)).toBe('1.00 KB')
    expect(formatBytes(1536, 2)).toBe('1.50 KB')
    expect(formatBytes(1048576, 2)).toBe('1.00 MB')
    expect(formatBytes(1073741824, 2)).toBe('1.00 GB')
    expect(formatBytes(1099511627776, 2)).toBe('1.00 TB')
  })

  it('honors decimal places parameter', () => {
    expect(formatBytes(1536, 0)).toBe('2 KB')
    expect(formatBytes(1536, 1)).toBe('1.5 KB')
    expect(formatBytes(1536, 3)).toBe('1.500 KB')
  })
})

describe('formatDate', () => {
  it('returns empty string on empty input', () => {
    expect(formatDate(null)).toBe('')
    expect(formatDate(undefined)).toBe('')
    expect(formatDate('')).toBe('')
  })

  it('formats ISO date string properly', () => {
    const result = formatDate('2026-03-15T12:00:00Z')
    expect(result).toBeTruthy()
    expect(result).toContain('2026')
  })
})

describe('getFileCategory', () => {
  it('classifies images correctly', () => {
    expect(getFileCategory('photo.png', 'image/png')).toBe('image')
    expect(getFileCategory('graphic.webp')).toBe('image')
  })

  it('classifies videos correctly', () => {
    expect(getFileCategory('clip.mp4', 'video/mp4')).toBe('video')
    expect(getFileCategory('movie.mkv')).toBe('video')
  })

  it('classifies audio correctly', () => {
    expect(getFileCategory('song.mp3', 'audio/mpeg')).toBe('audio')
    expect(getFileCategory('track.flac')).toBe('audio')
  })

  it('classifies PDF documents', () => {
    expect(getFileCategory('paper.pdf', 'application/pdf')).toBe('pdf')
    expect(getFileCategory('manual.pdf')).toBe('pdf')
  })

  it('classifies code and scripts', () => {
    expect(getFileCategory('main.py')).toBe('code')
    expect(getFileCategory('App.tsx')).toBe('code')
    expect(getFileCategory('script.sh')).toBe('code')
  })

  it('classifies archives', () => {
    expect(getFileCategory('backup.zip', 'application/zip')).toBe('archive')
    expect(getFileCategory('data.tar.gz')).toBe('archive')
  })

  it('falls back to generic for unknown types', () => {
    expect(getFileCategory('unknown.bin', 'application/octet-stream')).toBe('generic')
  })
})
