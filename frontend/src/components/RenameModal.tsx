import React, { useState, useEffect } from 'react';
import { X, Edit2, AlertCircle } from 'lucide-react';
import { filesApi, foldersApi, parseApiError } from '../api/client';
import type { FileItem, FolderItem } from '../types';

interface RenameModalProps {
  isOpen: boolean;
  onClose: () => void;
  item: FileItem | FolderItem | null;
  isFolder: boolean;
  onRenamed: () => void;
}

export const RenameModal: React.FC<RenameModalProps> = ({
  isOpen,
  onClose,
  item,
  isFolder,
  onRenamed,
}) => {
  const [name, setName] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (item) {
      setName(isFolder ? (item as FolderItem).name : (item as FileItem).filename);
      setError(null);
    }
  }, [item, isFolder]);

  if (!isOpen || !item) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const cleanName = name.trim();
    if (!cleanName) {
      setError('Name cannot be empty');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      if (isFolder) {
        await foldersApi.rename(item.id, cleanName);
      } else {
        await filesApi.rename(item.id, cleanName);
      }
      onRenamed();
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
            <div className="p-2 rounded-xl bg-sky-500/10 border border-sky-500/20 text-sky-400">
              <Edit2 className="w-5 h-5" />
            </div>
            <h3 className="text-lg font-bold text-white">
              Rename {isFolder ? 'Folder' : 'File'}
            </h3>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="mt-5 space-y-4">
          <div>
            <label className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-1.5">
              New Name
            </label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              autoFocus
              required
              className="w-full px-4 py-2.5 rounded-xl bg-slate-900/80 border border-slate-700/80 text-white placeholder-slate-500 text-sm focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500 transition-all"
            />
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
              type="submit"
              disabled={loading}
              className="px-5 py-2 rounded-xl text-xs sm:text-sm font-semibold text-white bg-gradient-to-r from-brand-600 to-sky-500 hover:from-brand-500 hover:to-sky-400 disabled:opacity-50 transition-all cursor-pointer"
            >
              {loading ? 'Saving...' : 'Save Name'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
