import React, { useState, useEffect } from 'react';
import { X, FolderInput, Folder as FolderIcon, Home, AlertCircle } from 'lucide-react';
import { filesApi, foldersApi, parseApiError } from '../api/client';
import type { FileItem, FolderItem } from '../types';

interface MoveModalProps {
  isOpen: boolean;
  onClose: () => void;
  file: FileItem | null;
  onMoved: () => void;
}

export const MoveModal: React.FC<MoveModalProps> = ({
  isOpen,
  onClose,
  file,
  onMoved,
}) => {
  const [folders, setFolders] = useState<FolderItem[]>([]);
  const [selectedFolderId, setSelectedFolderId] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);
  const [fetchingFolders, setFetchingFolders] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (isOpen && file) {
      setSelectedFolderId(file.folderId);
      setError(null);
      loadAllFolders();
    }
  }, [isOpen, file]);

  const loadAllFolders = async () => {
    setFetchingFolders(true);
    try {
      // Fetch root folders and subfolders
      const rootFolders = await foldersApi.list(null);
      const all: FolderItem[] = [...rootFolders];

      for (const rf of rootFolders) {
        try {
          const subs = await foldersApi.list(rf.id);
          all.push(...subs);
        } catch {
          // Ignore subfolder fetch errors
        }
      }
      setFolders(all);
    } catch (err) {
      setError(parseApiError(err));
    } finally {
      setFetchingFolders(false);
    }
  };

  if (!isOpen || !file) return null;

  const handleMove = async () => {
    setLoading(true);
    setError(null);

    try {
      await filesApi.move(file.id, selectedFolderId);
      onMoved();
      onClose();
    } catch (err) {
      setError(parseApiError(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-fadeIn">
      <div className="glass-modal w-full max-w-md rounded-2xl p-6 shadow-2xl shadow-black relative">
        <div className="flex items-center justify-between pb-4 border-b border-slate-800">
          <div className="flex items-center space-x-2.5">
            <div className="p-2 rounded-xl bg-amber-500/10 border border-amber-500/20 text-amber-400">
              <FolderInput className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-lg font-bold text-white">Move File</h3>
              <p className="text-xs text-slate-400 truncate max-w-[240px]">{file.filename}</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="mt-5 space-y-4">
          <p className="text-xs font-semibold text-slate-300 uppercase tracking-wider">
            Select Destination Folder
          </p>

          {fetchingFolders ? (
            <div className="py-8 text-center text-xs text-slate-400">
              <div className="w-5 h-5 border-2 border-brand-500/20 border-t-brand-500 rounded-full animate-spin mx-auto mb-2" />
              Loading folders...
            </div>
          ) : (
            <div className="max-h-56 overflow-y-auto space-y-1.5 p-1 rounded-xl bg-slate-900/60 border border-slate-800">
              {/* Root folder option */}
              <button
                onClick={() => setSelectedFolderId(null)}
                className={`w-full flex items-center space-x-2.5 px-3 py-2.5 rounded-lg text-xs font-medium transition-colors cursor-pointer text-left ${
                  selectedFolderId === null
                    ? 'bg-brand-600/30 text-brand-300 border border-brand-500/40'
                    : 'text-slate-300 hover:bg-slate-800 hover:text-white'
                }`}
              >
                <Home className="w-4 h-4 text-brand-400" />
                <span>Root (Top Level)</span>
              </button>

              {/* Folders */}
              {folders.map((f) => {
                const isSelected = selectedFolderId === f.id;
                return (
                  <button
                    key={f.id}
                    onClick={() => setSelectedFolderId(f.id)}
                    className={`w-full flex items-center space-x-2.5 px-3 py-2.5 rounded-lg text-xs font-medium transition-colors cursor-pointer text-left ${
                      isSelected
                        ? 'bg-brand-600/30 text-brand-300 border border-brand-500/40'
                        : 'text-slate-300 hover:bg-slate-800 hover:text-white'
                    }`}
                  >
                    <FolderIcon className="w-4 h-4 text-amber-400" />
                    <span className="truncate">{f.name}</span>
                  </button>
                );
              })}
            </div>
          )}

          {error && (
            <div className="p-3 rounded-xl bg-rose-500/10 border border-rose-500/20 text-rose-300 text-xs flex items-start space-x-2">
              <AlertCircle className="w-4 h-4 flex-shrink-0 mt-0.5" />
              <span>{error}</span>
            </div>
          )}

          <div className="flex justify-end space-x-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              disabled={loading}
              className="px-4 py-2 rounded-xl text-xs sm:text-sm font-medium text-slate-300 hover:bg-slate-800 border border-slate-700/80 transition-colors cursor-pointer"
            >
              Cancel
            </button>
            <button
              onClick={handleMove}
              disabled={loading || selectedFolderId === file.folderId}
              className="px-5 py-2 rounded-xl text-xs sm:text-sm font-semibold text-white bg-gradient-to-r from-brand-600 to-sky-500 hover:from-brand-500 hover:to-sky-400 disabled:opacity-50 transition-all cursor-pointer"
            >
              {loading ? 'Moving...' : 'Move Here'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
