import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { UploadModal } from './UploadModal';
import { transfersApi } from '../api/client';

vi.mock('../api/client', () => ({
  transfersApi: {
    initiate: vi.fn(),
    uploadChunk: vi.fn(),
    getStatus: vi.fn(),
    complete: vi.fn(),
    cancel: vi.fn(),
  },
  parseApiError: (err: unknown) => (err as Error)?.message || 'Upload error',
}));

describe('UploadModal', () => {
  const defaultProps = {
    isOpen: true,
    onClose: vi.fn(),
    folderId: null,
    folderName: 'Home',
    onUploadComplete: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it('renders correctly when open', () => {
    render(<UploadModal {...defaultProps} />);
    expect(screen.getByText('Upload File (Resumable)')).toBeInTheDocument();
    expect(screen.getByText('Home')).toBeInTheDocument();
    expect(screen.getByText(/Click or drag & drop a file here/i)).toBeInTheDocument();
  });

  it('does not render when isOpen is false', () => {
    const { container } = render(<UploadModal {...defaultProps} isOpen={false} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('handles file selection and initiates chunked upload', async () => {
    const file = new File(['mock content here'], 'sample.txt', { type: 'text/plain' });

    vi.mocked(transfersApi.initiate).mockResolvedValue({
      uploadId: 'test-upload-123',
      filename: 'sample.txt',
      fileSize: file.size,
      chunkSize: 5242880,
      totalChunks: 1,
      folderId: null,
      status: 'INITIATED',
      createdAt: new Date().toISOString(),
      expiresAt: new Date(Date.now() + 86400000).toISOString(),
    });

    vi.mocked(transfersApi.uploadChunk).mockResolvedValue({
      uploadId: 'test-upload-123',
      chunkNumber: 0,
      chunkSize: file.size,
      checksum: 'sha',
      uploadedChunksCount: 1,
      totalChunks: 1,
      status: 'UPLOADING',
    });

    vi.mocked(transfersApi.complete).mockResolvedValue({
      id: 99,
      filename: 'sample.txt',
      size: file.size,
      contentType: 'text/plain',
      folderId: null,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    });

    render(<UploadModal {...defaultProps} />);

    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    Object.defineProperty(input, 'files', {
      value: [file],
    });
    fireEvent.change(input);

    expect(screen.getByText('sample.txt')).toBeInTheDocument();

    const uploadButton = screen.getByText('Start Upload');
    fireEvent.click(uploadButton);

    await waitFor(() => {
      expect(transfersApi.initiate).toHaveBeenCalledWith(
        expect.objectContaining({
          filename: 'sample.txt',
          fileSize: file.size,
        })
      );
    });

    await waitFor(() => {
      expect(transfersApi.uploadChunk).toHaveBeenCalled();
    });

    await waitFor(() => {
      expect(transfersApi.complete).toHaveBeenCalledWith('test-upload-123');
    });

    await waitFor(() => {
      expect(defaultProps.onUploadComplete).toHaveBeenCalled();
    });
  });

  it('allows cancelling an active upload session', async () => {
    const file = new File(['test file content'], 'cancel-me.txt', { type: 'text/plain' });

    vi.mocked(transfersApi.cancel).mockResolvedValue(undefined);

    render(<UploadModal {...defaultProps} />);

    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    Object.defineProperty(input, 'files', {
      value: [file],
    });
    fireEvent.change(input);

    expect(screen.getByText('cancel-me.txt')).toBeInTheDocument();

    const closeButton = screen.getByText('Close');
    fireEvent.click(closeButton);
    expect(defaultProps.onClose).toHaveBeenCalled();
  });
});
