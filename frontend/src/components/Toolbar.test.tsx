import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { Toolbar } from './Toolbar'

describe('Toolbar component', () => {
  it('handles search input and clear button', () => {
    const onSearchChange = vi.fn()

    const { rerender } = render(
      <Toolbar
        searchQuery=""
        onSearchChange={onSearchChange}
        onUploadClick={vi.fn()}
        onNewFolderClick={vi.fn()}
        viewMode="grid"
        onViewModeChange={vi.fn()}
        sortField="name"
        onSortFieldChange={vi.fn()}
        sortOrder="asc"
        onSortOrderToggle={vi.fn()}
        onRefresh={vi.fn()}
        isRefreshing={false}
      />
    )

    const input = screen.getByPlaceholderText(/Search files in your cloud/i)
    fireEvent.change(input, { target: { value: 'invoice' } })
    expect(onSearchChange).toHaveBeenCalledWith('invoice')

    // Rerender with searchQuery set
    rerender(
      <Toolbar
        searchQuery="invoice"
        onSearchChange={onSearchChange}
        onUploadClick={vi.fn()}
        onNewFolderClick={vi.fn()}
        viewMode="grid"
        onViewModeChange={vi.fn()}
        sortField="name"
        onSortFieldChange={vi.fn()}
        sortOrder="asc"
        onSortOrderToggle={vi.fn()}
        onRefresh={vi.fn()}
        isRefreshing={false}
      />
    )

    // Clear button should be present
    const clearBtn = input.parentElement?.querySelector('button')
    expect(clearBtn).toBeTruthy()
    if (clearBtn) fireEvent.click(clearBtn)
    expect(onSearchChange).toHaveBeenCalledWith('')
  })

  it('triggers view mode and action clicks', () => {
    const onUploadClick = vi.fn()
    const onNewFolderClick = vi.fn()
    const onViewModeChange = vi.fn()
    const onRefresh = vi.fn()

    render(
      <Toolbar
        searchQuery=""
        onSearchChange={vi.fn()}
        onUploadClick={onUploadClick}
        onNewFolderClick={onNewFolderClick}
        viewMode="grid"
        onViewModeChange={onViewModeChange}
        sortField="name"
        onSortFieldChange={vi.fn()}
        sortOrder="asc"
        onSortOrderToggle={vi.fn()}
        onRefresh={onRefresh}
        isRefreshing={false}
      />
    )

    fireEvent.click(screen.getByText('Upload'))
    expect(onUploadClick).toHaveBeenCalledTimes(1)

    fireEvent.click(screen.getByText('New Folder'))
    expect(onNewFolderClick).toHaveBeenCalledTimes(1)

    fireEvent.click(screen.getByTitle('List view'))
    expect(onViewModeChange).toHaveBeenCalledWith('list')

    fireEvent.click(screen.getByTitle('Refresh files'))
    expect(onRefresh).toHaveBeenCalledTimes(1)
  })
})
