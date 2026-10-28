import React, { useState, useEffect, useCallback, useMemo } from 'react';
import {
  authApi,
  filesApi,
  foldersApi,
  storageApi,
  parseApiError,
  getStoredToken,
} from './api/client';
import type {
  User,
  FileItem,
  FolderItem,
  Breadcrumb,
  StorageStats,
  ViewMode,
  SortField,
  SortOrder,
} from './types';

import { Navbar } from './components/Navbar';
import { AuthView } from './components/AuthView';
import { BreadcrumbBar } from './components/BreadcrumbBar';
import { Toolbar } from './components/Toolbar';
import { GridItem } from './components/GridItem';
import { ListItem } from './components/ListItem';
import { EmptyState } from './components/EmptyState';
import { UploadModal } from './components/UploadModal';
import { NewFolderModal } from './components/NewFolderModal';
import { RenameModal } from './components/RenameModal';
import { MoveModal } from './components/MoveModal';
import { DeleteModal } from './components/DeleteModal';

export const App: React.FC = () => {
  // Auth state
  const [user, setUser] = useState<User | null>(null);
  const [loadingUser, setLoadingUser] = useState(true);

  // Content state
  const [currentFolderId, setCurrentFolderId] = useState<number | null>(null);
  const [currentFolderName, setCurrentFolderName] = useState<string>('Root');
  const [breadcrumbs, setBreadcrumbs] = useState<Breadcrumb[]>([]);
  const [folders, setFolders] = useState<FolderItem[]>([]);
  const [files, setFiles] = useState<FileItem[]>([]);
  const [storageStats, setStorageStats] = useState<StorageStats | null>(null);

  // Search state
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<FileItem[] | null>(null);
  const [isSearching, setIsSearching] = useState(false);

  // UI state
  const [viewMode, setViewMode] = useState<ViewMode>(() => {
    return (localStorage.getItem('homecloud_view_mode') as ViewMode) || 'grid';
  });
  const [sortField, setSortField] = useState<SortField>('name');
  const [sortOrder, setSortOrder] = useState<SortOrder>('asc');
  const [loadingContent, setLoadingContent] = useState(false);
  const [generalError, setGeneralError] = useState<string | null>(null);

  // Modals state
  const [uploadModalOpen, setUploadModalOpen] = useState(false);
  const [newFolderModalOpen, setNewFolderModalOpen] = useState(false);
  const [renameTarget, setRenameTarget] = useState<{
    item: FileItem | FolderItem;
    isFolder: boolean;
  } | null>(null);
  const [moveTarget, setMoveTarget] = useState<FileItem | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<{
    item: FileItem | FolderItem;
    isFolder: boolean;
  } | null>(null);

  // 1. Initial auth check
  useEffect(() => {
    const initAuth = async () => {
      const token = getStoredToken();
      if (!token) {
        setLoadingUser(false);
        return;
      }
      try {
        const me = await authApi.getMe();
        setUser(me);
      } catch {
        authApi.logout();
        setUser(null);
      } finally {
        setLoadingUser(false);
      }
    };

    initAuth();

    const handleUnauthorized = () => {
      setUser(null);
    };
    window.addEventListener('homecloud:unauthorized', handleUnauthorized);
    return () => window.removeEventListener('homecloud:unauthorized', handleUnauthorized);
  }, []);

  // 2. Fetch stats
  const loadStats = useCallback(async () => {
    if (!user) return;
    try {
      const stats = await storageApi.getStats();
      setStorageStats(stats);
    } catch {
      // Non-critical, ignore
    }
  }, [user]);

  // 3. Fetch folder contents & breadcrumbs
  const loadFolderContent = useCallback(async () => {
    if (!user) return;
    setLoadingContent(true);
    setGeneralError(null);

    try {
      // Parallel fetch for files and folders
      const [fetchedFolders, fetchedFiles] = await Promise.all([
        foldersApi.list(currentFolderId),
        filesApi.list(currentFolderId),
      ]);

      setFolders(fetchedFolders);
      setFiles(fetchedFiles);

      // Fetch breadcrumbs if inside a subfolder
      if (currentFolderId != null) {
        const detail = await foldersApi.getDetails(currentFolderId);
        setBreadcrumbs(detail.breadcrumbs || []);
        setCurrentFolderName(detail.name);
      } else {
        setBreadcrumbs([]);
        setCurrentFolderName('Root');
      }

      // Refresh storage stats
      loadStats();
    } catch (err) {
      setGeneralError(parseApiError(err));
    } finally {
      setLoadingContent(false);
    }
  }, [user, currentFolderId, loadStats]);

  useEffect(() => {
    if (user && !searchQuery) {
      loadFolderContent();
    }
  }, [user, currentFolderId, loadFolderContent, searchQuery]);

  // 4. Handle search
  useEffect(() => {
    if (!user) return;
    const trimmed = searchQuery.trim();
    if (!trimmed) {
      setSearchResults(null);
      return;
    }

    const timer = setTimeout(async () => {
      setIsSearching(true);
      try {
        const results = await filesApi.search(trimmed);
        setSearchResults(results);
      } catch (err) {
        setGeneralError(parseApiError(err));
      } finally {
        setIsSearching(false);
      }
    }, 250);

    return () => clearTimeout(timer);
  }, [user, searchQuery]);

  // 5. Sorting
  const sortedFolders = useMemo(() => {
    return [...folders].sort((a, b) => {
      let cmp = 0;
      if (sortField === 'name') {
        cmp = a.name.localeCompare(b.name);
      } else if (sortField === 'date') {
        const dateA = new Date(a.updatedAt || a.createdAt).getTime();
        const dateB = new Date(b.updatedAt || b.createdAt).getTime();
        cmp = dateA - dateB;
      }
      return sortOrder === 'asc' ? cmp : -cmp;
    });
  }, [folders, sortField, sortOrder]);

  const displayedFiles = searchResults !== null ? searchResults : files;

  const sortedFiles = useMemo(() => {
    return [...displayedFiles].sort((a, b) => {
      let cmp = 0;
      if (sortField === 'name') {
        cmp = a.filename.localeCompare(b.filename);
      } else if (sortField === 'size') {
        cmp = a.size - b.size;
      } else if (sortField === 'date') {
        const dateA = new Date(a.updatedAt || a.createdAt).getTime();
        const dateB = new Date(b.updatedAt || b.createdAt).getTime();
        cmp = dateA - dateB;
      }
      return sortOrder === 'asc' ? cmp : -cmp;
    });
  }, [displayedFiles, sortField, sortOrder]);

  const handleViewModeChange = (mode: ViewMode) => {
    setViewMode(mode);
    localStorage.setItem('homecloud_view_mode', mode);
  };

  const handleDownloadFile = async (fileId: number, filename: string) => {
    try {
      await filesApi.download(fileId, filename);
    } catch (err) {
      setGeneralError(parseApiError(err));
    }
  };

  const handleLogout = () => {
    authApi.logout();
    setUser(null);
    setFiles([]);
    setFolders([]);
    setCurrentFolderId(null);
  };

  // Loading screen during initial token verification
  if (loadingUser) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-950">
        <div className="text-center">
          <div className="w-12 h-12 border-3 border-brand-500/20 border-t-brand-500 rounded-full animate-spin mx-auto mb-3" />
          <p className="text-sm text-slate-400 font-medium">Connecting to HomeCloud...</p>
        </div>
      </div>
    );
  }

  // Unauthenticated view
  if (!user) {
    return (
      <div className="min-h-screen bg-slate-950">
        <Navbar user={null} storageStats={null} onLogout={handleLogout} />
        <AuthView onAuthSuccess={(authenticatedUser) => setUser(authenticatedUser)} />
      </div>
    );
  }

  // Authenticated Dashboard
  const isEmpty =
    searchResults !== null
      ? searchResults.length === 0
      : folders.length === 0 && files.length === 0;

  return (
    <div className="min-h-screen bg-slate-950 flex flex-col">
      {/* Top Navigation */}
      <Navbar user={user} storageStats={storageStats} onLogout={handleLogout} />

      {/* Main Content Area */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-6">
        {/* Navigation Breadcrumbs & Location info */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 pb-2">
          {searchQuery ? (
            <div className="text-sm text-slate-300 font-medium">
              Search results for: <span className="text-brand-400 font-semibold">"{searchQuery}"</span>
            </div>
          ) : (
            <BreadcrumbBar
              currentFolderId={currentFolderId}
              breadcrumbs={breadcrumbs}
              onNavigate={(id) => setCurrentFolderId(id)}
            />
          )}
        </div>

        {/* Action Toolbar */}
        <Toolbar
          searchQuery={searchQuery}
          onSearchChange={setSearchQuery}
          onUploadClick={() => setUploadModalOpen(true)}
          onNewFolderClick={() => setNewFolderModalOpen(true)}
          viewMode={viewMode}
          onViewModeChange={handleViewModeChange}
          sortField={sortField}
          onSortFieldChange={setSortField}
          sortOrder={sortOrder}
          onSortOrderToggle={() => setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc')}
          onRefresh={loadFolderContent}
          isRefreshing={loadingContent || isSearching}
        />

        {/* Global error banner if any */}
        {generalError && (
          <div className="my-3 p-3 rounded-xl bg-rose-500/10 border border-rose-500/20 text-rose-300 text-xs flex items-center justify-between">
            <span>{generalError}</span>
            <button
              onClick={() => setGeneralError(null)}
              className="text-slate-400 hover:text-white text-xs font-semibold"
            >
              Dismiss
            </button>
          </div>
        )}

        {/* File and Folder views */}
        {loadingContent && !folders.length && !files.length ? (
          <div className="py-24 text-center">
            <div className="w-10 h-10 border-2 border-brand-500/20 border-t-brand-500 rounded-full animate-spin mx-auto mb-3" />
            <p className="text-sm text-slate-400 font-medium">Loading files...</p>
          </div>
        ) : isEmpty ? (
          <EmptyState
            isSearch={searchQuery.length > 0}
            onUploadClick={() => setUploadModalOpen(true)}
            onNewFolderClick={() => setNewFolderModalOpen(true)}
          />
        ) : viewMode === 'grid' ? (
          <div className="mt-4 space-y-6">
            {/* Folders Section (only shown when not searching) */}
            {searchResults === null && sortedFolders.length > 0 && (
              <div>
                <h3 className="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-3">
                  Folders ({sortedFolders.length})
                </h3>
                <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3.5">
                  {sortedFolders.map((folder) => (
                    <GridItem
                      key={`folder-${folder.id}`}
                      item={folder}
                      isFolder={true}
                      onOpenFolder={(id) => setCurrentFolderId(id)}
                      onRename={(item, isFolder) => setRenameTarget({ item, isFolder })}
                      onDelete={(item, isFolder) => setDeleteTarget({ item, isFolder })}
                    />
                  ))}
                </div>
              </div>
            )}

            {/* Files Section */}
            {sortedFiles.length > 0 && (
              <div>
                <h3 className="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-3">
                  Files ({sortedFiles.length})
                </h3>
                <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3.5">
                  {sortedFiles.map((file) => (
                    <GridItem
                      key={`file-${file.id}`}
                      item={file}
                      isFolder={false}
                      onDownload={handleDownloadFile}
                      onRename={(item, isFolder) => setRenameTarget({ item, isFolder })}
                      onMove={(file) => setMoveTarget(file)}
                      onDelete={(item, isFolder) => setDeleteTarget({ item, isFolder })}
                    />
                  ))}
                </div>
              </div>
            )}
          </div>
        ) : (
          /* List View */
          <div className="mt-4 glass-panel rounded-2xl border border-slate-800/80 overflow-hidden">
            {/* Header */}
            <div className="flex items-center justify-between px-4 py-2.5 bg-slate-900/80 border-b border-slate-800 text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
              <span className="flex-1">Name</span>
              <span className="hidden sm:block w-36">Modified</span>
              <span className="hidden md:block w-24 text-right">Size</span>
              <span className="w-24 text-right">Actions</span>
            </div>

            <div className="divide-y divide-slate-800/50">
              {/* Folders first */}
              {searchResults === null &&
                sortedFolders.map((folder) => (
                  <ListItem
                    key={`folder-${folder.id}`}
                    item={folder}
                    isFolder={true}
                    onOpenFolder={(id) => setCurrentFolderId(id)}
                    onRename={(item, isFolder) => setRenameTarget({ item, isFolder })}
                    onDelete={(item, isFolder) => setDeleteTarget({ item, isFolder })}
                  />
                ))}

              {/* Files */}
              {sortedFiles.map((file) => (
                <ListItem
                  key={`file-${file.id}`}
                  item={file}
                  isFolder={false}
                  onDownload={handleDownloadFile}
                  onRename={(item, isFolder) => setRenameTarget({ item, isFolder })}
                  onMove={(file) => setMoveTarget(file)}
                  onDelete={(item, isFolder) => setDeleteTarget({ item, isFolder })}
                />
              ))}
            </div>
          </div>
        )}
      </main>

      {/* Modals */}
      <UploadModal
        isOpen={uploadModalOpen}
        onClose={() => setUploadModalOpen(false)}
        folderId={currentFolderId}
        folderName={currentFolderName}
        onUploadComplete={loadFolderContent}
      />

      <NewFolderModal
        isOpen={newFolderModalOpen}
        onClose={() => setNewFolderModalOpen(false)}
        parentId={currentFolderId}
        onFolderCreated={loadFolderContent}
      />

      <RenameModal
        isOpen={renameTarget !== null}
        onClose={() => setRenameTarget(null)}
        item={renameTarget?.item || null}
        isFolder={renameTarget?.isFolder || false}
        onRenamed={loadFolderContent}
      />

      <MoveModal
        isOpen={moveTarget !== null}
        onClose={() => setMoveTarget(null)}
        file={moveTarget}
        onMoved={loadFolderContent}
      />

      <DeleteModal
        isOpen={deleteTarget !== null}
        onClose={() => setDeleteTarget(null)}
        item={deleteTarget?.item || null}
        isFolder={deleteTarget?.isFolder || false}
        onDeleted={loadFolderContent}
      />
    </div>
  );
};

export default App;
