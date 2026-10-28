import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { AuthView } from './AuthView'
import { authApi } from '../api/client'
import type { User, LoginResponse } from '../types'

vi.mock('../api/client', () => ({
  authApi: {
    login: vi.fn(),
    register: vi.fn(),
    getMe: vi.fn(),
  },
  parseApiError: (err: unknown) => (err instanceof Error ? err.message : 'Authentication failed'),
}))

describe('AuthView component', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders sign in by default and toggles to register', () => {
    render(<AuthView onAuthSuccess={vi.fn()} />)

    expect(screen.getByRole('heading', { name: /Sign in to HomeCloud/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Sign In/i })).toBeInTheDocument()
    expect(screen.queryByPlaceholderText(/Confirm Password/i)).not.toBeInTheDocument()

    // Click 'Register now'
    fireEvent.click(screen.getByText('Register now'))

    expect(screen.getByRole('heading', { name: /Create your HomeCloud account/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Create Account/i })).toBeInTheDocument()
    expect(screen.getByText('Confirm Password')).toBeInTheDocument()
  })

  it('shows validation error when passwords do not match during registration', async () => {
    render(<AuthView onAuthSuccess={vi.fn()} />)

    // Switch to register
    fireEvent.click(screen.getByText('Register now'))

    // Fill in email
    fireEvent.change(screen.getByPlaceholderText('user@example.com'), {
      target: { value: 'test@example.com' },
    })

    // Fill in passwords that do not match
    const passwordInputs = screen.getAllByPlaceholderText('••••••••')
    fireEvent.change(passwordInputs[0], { target: { value: 'password123' } })
    fireEvent.change(passwordInputs[1], { target: { value: 'mismatch456' } })

    fireEvent.click(screen.getByRole('button', { name: /Create Account/i }))

    expect(await screen.findByText('Passwords do not match')).toBeInTheDocument()
    expect(authApi.register).not.toHaveBeenCalled()
  })

  it('successfully logs in and calls onAuthSuccess', async () => {
    const mockUser: User = {
      id: 1,
      email: 'user@homecloud.local',
    }

    const mockLoginResponse: LoginResponse = {
      message: 'Login successful',
      userId: 1,
      email: 'user@homecloud.local',
      token: 'jwt.token.here',
    }

    vi.mocked(authApi.login).mockResolvedValue(mockLoginResponse)
    vi.mocked(authApi.getMe).mockResolvedValue(mockUser)

    const onAuthSuccess = vi.fn()
    render(<AuthView onAuthSuccess={onAuthSuccess} />)

    fireEvent.change(screen.getByPlaceholderText('user@example.com'), {
      target: { value: 'user@homecloud.local' },
    })
    fireEvent.change(screen.getByPlaceholderText('••••••••'), {
      target: { value: 'secret123' },
    })

    fireEvent.click(screen.getByRole('button', { name: /Sign In/i }))

    await waitFor(() => {
      expect(authApi.login).toHaveBeenCalledWith('user@homecloud.local', 'secret123')
      expect(authApi.getMe).toHaveBeenCalledTimes(1)
      expect(onAuthSuccess).toHaveBeenCalledWith(mockUser)
    })
  })
})
