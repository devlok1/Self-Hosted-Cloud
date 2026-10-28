import axios, { AxiosError } from 'axios';
import type {
  User,
  FileItem,
  FolderItem,
  FolderDetail,
  StorageStats,
  LoginResponse,
  ApiError,
} from '../types';


const TOKEN_KEY = 'homecloud_jwt_token';

export const getStoredToken = (): string | null => {
  return localStorage.getItem(TOKEN_KEY);
};

export const setStoredToken = (token: string): void => {
  localStorage.setItem(TOKEN_KEY, token);
};

export const clearStoredToken = (): void => {
  localStorage.removeItem(TOKEN_KEY);
};

const api = axios.create({
  baseURL: '',
  headers: {
    'Content-Type': 'application/json',
  },
});

api.interceptors.request.use((config) => {
  const token = getStoredToken();
  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiError>) => {
    if (error.response?.status === 401) {
      // Don't auto-clear token if the request was to login itself
      if (!error.config?.url?.includes('/api/auth/login')) {
        clearStoredToken();
        window.dispatchEvent(new CustomEvent('homecloud:unauthorized'));
      }
    }
    return Promise.reject(error);
  }
);

export const parseApiError = (error: unknown): string => {
  if (axios.isAxiosError(error)) {
    const errData = error.response?.data as ApiError | undefined;
    if (errData?.validationErrors) {
      return Object.values(errData.validationErrors).join(', ');
    }
    if (errData?.message) {
      return errData.message;
    }
    if (errData?.error) {
      return errData.error;
    }
    if (error.message) {
      return error.message;
    }
  }
  return 'An unexpected error occurred. Please try again.';
};

export const authApi = {
  register: async (email: string, password: string): Promise<User> => {
    const res = await api.post<User>('/api/auth/register', { email, password });
    return res.data;
  },

  login: async (email: string, password: string): Promise<LoginResponse> => {
    const res = await api.post<LoginResponse>('/api/auth/login', { email, password });
    if (res.data.token) {
      setStoredToken(res.data.token);
    }
    return res.data;
  },

  getMe: async (): Promise<User> => {
    const res = await api.get<User>('/api/auth/me');
    return res.data;
  },

  logout: (): void => {
    clearStoredToken();
  },
};

export const filesApi = {
  list: async (folderId?: number | null): Promise<FileItem[]> => {
    const params = folderId != null ? { folderId } : {};
    const res = await api.get<FileItem[]>('/api/files', { params });
    return res.data;
  },

  search: async (q: string): Promise<FileItem[]> => {
    const res = await api.get<FileItem[]>('/api/files/search', { params: { q } });
    return res.data;
  },

  upload: async (
    file: File,
    folderId?: number | null,
    onProgress?: (percent: number) => void
  ): Promise<FileItem> => {
    const formData = new FormData();
    formData.append('file', file);
    if (folderId != null) {
      formData.append('folderId', String(folderId));
    }

    const res = await api.post<FileItem>('/api/files/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
      onUploadProgress: (progressEvent) => {
        if (progressEvent.total && onProgress) {
          const percent = Math.round((progressEvent.loaded * 100) / progressEvent.total);
          onProgress(percent);
        }
      },
    });

    return res.data;
  },

  download: async (fileId: number, filename: string): Promise<void> => {
    const res = await api.get(`/api/files/${fileId}/download`, {
      responseType: 'blob',
    });

    const url = window.URL.createObjectURL(new Blob([res.data]));
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', filename);
    document.body.appendChild(link);
    link.click();
    link.parentNode?.removeChild(link);
    window.URL.revokeObjectURL(url);
  },

  rename: async (fileId: number, filename: string): Promise<FileItem> => {
    const res = await api.put<FileItem>(`/api/files/${fileId}`, null, {
      params: { filename },
    });
    return res.data;
  },

  move: async (fileId: number, folderId?: number | null): Promise<FileItem> => {
    const params = folderId != null ? { folderId } : {};
    const res = await api.put<FileItem>(`/api/files/${fileId}/move`, null, { params });
    return res.data;
  },

  delete: async (fileId: number): Promise<void> => {
    await api.delete(`/api/files/${fileId}`);
  },
};

export const foldersApi = {
  list: async (parentId?: number | null): Promise<FolderItem[]> => {
    const params = parentId != null ? { parentId } : {};
    const res = await api.get<FolderItem[]>('/api/folders', { params });
    return res.data;
  },

  getDetails: async (folderId: number): Promise<FolderDetail> => {
    const res = await api.get<FolderDetail>(`/api/folders/${folderId}`);
    return res.data;
  },

  create: async (name: string, parentId?: number | null): Promise<FolderItem> => {
    const params: Record<string, string> = { name };
    if (parentId != null) {
      params.parentId = String(parentId);
    }
    const res = await api.post<FolderItem>('/api/folders', null, { params });
    return res.data;
  },

  rename: async (folderId: number, name: string): Promise<FolderItem> => {
    const res = await api.put<FolderItem>(`/api/folders/${folderId}`, null, {
      params: { name },
    });
    return res.data;
  },

  delete: async (folderId: number): Promise<void> => {
    await api.delete(`/api/folders/${folderId}`);
  },
};

export const storageApi = {
  getStats: async (): Promise<StorageStats> => {
    const res = await api.get<StorageStats>('/api/storage/stats');
    return res.data;
  },
};
