import React, { useState } from 'react';
import { X, Trash2, AlertTriangle, AlertCircle } from 'lucide-react';
import { filesApi, foldersApi, parseApiError } from '../api/client';
import type { FileItem, FolderItem } from '../types';

interface DeleteModalProps {
  isOpen: boolean;
  onClose: () => void;
  item: FileItem | FolderItem | null;
  isFolder: boolean;
  onDeleted: () => void;
}

export const DeleteModal: React.FC<DeleteModalProps> = ({
  isOpen,
  onClose,
  item,
  isFolder,
  onDeleted,
}) => {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen || !item) return null;

  const name = isFolder ? (item as FolderItem).name : (item as FileItem).filename;

  const handleDelete = async () => {
    setLoading(true);
    setError(null);

    try {
      if (isFolder) {
        await foldersApi.delete(item.id);
      } else {
        await filesApi.delete(item.id);
      }
      onDeleted();
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
            <div className="p-2 rounded-xl bg-rose-500/10 border border-rose-500/20 text-rose-400">
              <Trash2 className="w-5 h-5" />
            </div>
            <h3 className="text-lg font-bold text-white">
              Delete {isFolder ? 'Folder' : 'File'}
            </h3>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="mt-5 space-y-4">
          <div className="p-3.5 rounded-xl bg-amber-500/10 border border-amber-500/20 text-amber-300 text-xs flex items-start space-x-2.5">
            <AlertTriangle className="w-4 h-4 flex-shrink-0 mt-0.5 text-amber-400" />
            <div>
              <p className="font-semibold text-amber-200">
                Are you sure you want to delete <strong className="text-white">"{name}"</strong>?
              </p>
              <p className="mt-1 text-slate-300">
                {isFolder
                  ? 'The folder must be empty (no files or subfolders) to be deleted.'
                  : 'This file will be permanently removed from your personal storage.'}
              </p>
            </div>
          </div>

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
              onClick={handleDelete}
              disabled={loading}
              className="px-5 py-2 rounded-xl text-xs sm:text-sm font-semibold text-white bg-rose-600 hover:bg-rose-500 disabled:opacity-50 transition-all cursor-pointer shadow-lg shadow-rose-600/20"
            >
              {loading ? 'Deleting...' : 'Delete Permanently'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
