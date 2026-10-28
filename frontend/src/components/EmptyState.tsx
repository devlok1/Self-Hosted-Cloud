import React from 'react';
import { FolderOpen, Upload, FolderPlus } from 'lucide-react';

interface EmptyStateProps {
  isSearch?: boolean;
  onUploadClick: () => void;
  onNewFolderClick: () => void;
}

export const EmptyState: React.FC<EmptyStateProps> = ({
  isSearch = false,
  onUploadClick,
  onNewFolderClick,
}) => {
  return (
    <div className="text-center py-20 px-4 glass-panel rounded-2xl border border-slate-800/80 my-4">
      <div className="w-16 h-16 rounded-2xl bg-slate-800/80 border border-slate-700/60 flex items-center justify-center mx-auto mb-4 text-slate-400">
        <FolderOpen className="w-8 h-8 text-slate-400" />
      </div>
      <h3 className="text-lg font-semibold text-white">
        {isSearch ? 'No files match your search' : 'This folder is empty'}
      </h3>
      <p className="text-sm text-slate-400 mt-1 max-w-sm mx-auto">
        {isSearch
          ? 'Try adjusting your search query or clear the filter to see all files.'
          : 'Upload your documents, media, or archives, or create subfolders to organize your cloud.'}
      </p>

      {!isSearch && (
        <div className="flex items-center justify-center space-x-3 mt-6">
          <button
            onClick={onNewFolderClick}
            className="flex items-center space-x-2 px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 text-sm font-medium border border-slate-700 transition-colors cursor-pointer"
          >
            <FolderPlus className="w-4 h-4 text-amber-400" />
            <span>New Folder</span>
          </button>
          <button
            onClick={onUploadClick}
            className="flex items-center space-x-2 px-4 py-2 rounded-xl bg-brand-600 hover:bg-brand-500 text-white text-sm font-medium transition-colors cursor-pointer"
          >
            <Upload className="w-4 h-4" />
            <span>Upload File</span>
          </button>
        </div>
      )}
    </div>
  );
};
