import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { EmptyState } from './EmptyState'

describe('EmptyState component', () => {
  it('renders default empty state with action buttons', () => {
    const onUploadClick = vi.fn()
    const onNewFolderClick = vi.fn()

    render(
      <EmptyState
        isSearch={false}
        onUploadClick={onUploadClick}
        onNewFolderClick={onNewFolderClick}
      />
    )

    expect(screen.getByText('This folder is empty')).toBeInTheDocument()
    expect(screen.getByText('Upload File')).toBeInTheDocument()
    expect(screen.getByText('New Folder')).toBeInTheDocument()

    fireEvent.click(screen.getByText('Upload File'))
    expect(onUploadClick).toHaveBeenCalledTimes(1)

    fireEvent.click(screen.getByText('New Folder'))
    expect(onNewFolderClick).toHaveBeenCalledTimes(1)
  })

  it('renders search empty state without upload buttons', () => {
    render(
      <EmptyState
        isSearch={true}
        onUploadClick={vi.fn()}
        onNewFolderClick={vi.fn()}
      />
    )

    expect(screen.getByText('No files match your search')).toBeInTheDocument()
    expect(screen.queryByText('Upload File')).not.toBeInTheDocument()
    expect(screen.queryByText('New Folder')).not.toBeInTheDocument()
  })
})
