import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { BreadcrumbBar } from './BreadcrumbBar'
import type { Breadcrumb } from '../types'

describe('BreadcrumbBar component', () => {
  it('renders root breadcrumb when at top level', () => {
    const onNavigate = vi.fn()
    render(<BreadcrumbBar currentFolderId={null} breadcrumbs={[]} onNavigate={onNavigate} />)

    const rootBtn = screen.getByText('Root')
    expect(rootBtn).toBeInTheDocument()
    fireEvent.click(rootBtn)
    expect(onNavigate).toHaveBeenCalledWith(null)
  })

  it('renders breadcrumb items and allows navigation', () => {
    const onNavigate = vi.fn()
    const crumbs: Breadcrumb[] = [
      { id: 10, name: 'Documents' },
      { id: 25, name: 'Work' },
    ]

    render(<BreadcrumbBar currentFolderId={25} breadcrumbs={crumbs} onNavigate={onNavigate} />)

    expect(screen.getByText('Root')).toBeInTheDocument()
    expect(screen.getByText('Documents')).toBeInTheDocument()
    expect(screen.getByText('Work')).toBeInTheDocument()

    // Clicking 'Documents' should navigate to folder 10
    fireEvent.click(screen.getByText('Documents'))
    expect(onNavigate).toHaveBeenCalledWith(10)

    // Current crumb (Work) should be disabled
    const currentCrumbBtn = screen.getByText('Work').closest('button')
    expect(currentCrumbBtn).toBeDisabled()
  })
})
