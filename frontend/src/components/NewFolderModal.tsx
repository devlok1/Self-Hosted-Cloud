import React, { useState } from 'react';
import { X, FolderPlus, AlertCircle } from 'lucide-react';
import { foldersApi, parseApiError } from '../api/client';

interface NewFolderModalProps {
  isOpen: boolean;
  onClose: () => void;
  parentId: number | null;
  onFolderCreated: () => void;
}

export const NewFolderModal: React.FC<NewFolderModalProps> = ({
  isOpen,
  onClose,
  parentId,
  onFolderCreated,
}) => {
  const [name, setName] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const cleanName = name.trim();
    if (!cleanName) {
      setError('Folder name cannot be empty');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      await foldersApi.create(cleanName, parentId);
      setName('');
      onFolderCreated();
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
              <FolderPlus className="w-5 h-5" />
            </div>
            <h3 className="text-lg font-bold text-white">Create New Folder</h3>
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
              Folder Name
            </label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Invoices, Photos, Documents"
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
              {loading ? 'Creating...' : 'Create Folder'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
