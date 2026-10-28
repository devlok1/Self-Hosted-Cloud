import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { Navbar } from './Navbar'
import type { User, StorageStats } from '../types'

describe('Navbar component', () => {
  it('renders branding and LAN status indicator', () => {
    render(<Navbar user={null} storageStats={null} onLogout={vi.fn()} />)
    expect(screen.getByText('HomeCloud')).toBeInTheDocument()
    expect(screen.getByText('LAN')).toBeInTheDocument()
    expect(screen.getByText('Self-Hosted Storage')).toBeInTheDocument()
  })

  it('renders user email, storage stats, and handles logout click', () => {
    const mockUser: User = {
      id: 1,
      email: 'test@homecloud.local',
    }

    const mockStats: StorageStats = {
      usedBytes: 1048576,
      fileCount: 42,
      folderCount: 5,
    }

    const onLogout = vi.fn()

    render(<Navbar user={mockUser} storageStats={mockStats} onLogout={onLogout} />)

    expect(screen.getByText('test@homecloud.local')).toBeInTheDocument()
    expect(screen.getByText('1.00 MB')).toBeInTheDocument()
    expect(screen.getByText('42 files')).toBeInTheDocument()

    const logoutButton = screen.getByTitle('Log Out')
    expect(logoutButton).toBeInTheDocument()
    fireEvent.click(logoutButton)
    expect(onLogout).toHaveBeenCalledTimes(1)
  })
})
