export interface User {
  id: number;
  email: string;
}

export interface FileItem {
  id: number;
  filename: string;
  size: number;
  contentType: string | null;
  folderId: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface FolderItem {
  id: number;
  name: string;
  parentId: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface Breadcrumb {
  id: number;
  name: string;
}

export interface FolderDetail {
  id: number;
  name: string;
  parentId: number | null;
  breadcrumbs: Breadcrumb[];
  createdAt: string;
  updatedAt: string;
}

export interface StorageStats {
  usedBytes: number;
  fileCount: number;
  folderCount: number;
}

export interface LoginResponse {
  message: string;
  userId: number;
  email: string;
  token: string;
}

export interface ApiError {
  status?: number;
  error?: string;
  message?: string;
  validationErrors?: Record<string, string>;
}

export type ViewMode = 'grid' | 'list';
export type SortField = 'name' | 'date' | 'size';
export type SortOrder = 'asc' | 'desc';

export type TransferStatus =
  | 'INITIATED'
  | 'UPLOADING'
  | 'FINALIZING'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED'
  | 'EXPIRED';

export interface TransferSession {
  uploadId: string;
  filename: string;
  fileSize: number;
  chunkSize: number;
  totalChunks: number;
  folderId: number | null;
  status: TransferStatus;
  createdAt: string;
  expiresAt: string;
}

export interface InitiateTransferRequest {
  filename: string;
  fileSize: number;
  chunkSize?: number;
  folderId?: number | null;
  contentType?: string;
  expectedChecksum?: string;
}

export interface TransferChunkResponse {
  uploadId: string;
  chunkNumber: number;
  chunkSize: number;
  checksum: string;
  uploadedChunksCount: number;
  totalChunks: number;
  status: TransferStatus;
}

export interface TransferStatusResponse {
  uploadId: string;
  filename: string;
  fileSize: number;
  chunkSize: number;
  totalChunks: number;
  uploadedChunksCount: number;
  uploadedChunks: number[];
  folderId: number | null;
  status: TransferStatus;
  expiresAt: string;
}
