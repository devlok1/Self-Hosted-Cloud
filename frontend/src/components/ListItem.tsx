import React from 'react';
import { Download, Edit2, FolderInput, Trash2 } from 'lucide-react';
import type { FileItem, FolderItem } from '../types';
import { FileIcon } from './FileIcon';
import { formatBytes, formatDate } from '../utils/formatters';

interface ListItemProps {
  item: FolderItem | FileItem;
  isFolder: boolean;
  onOpenFolder?: (folderId: number) => void;
  onDownload?: (fileId: number, filename: string) => void;
  onRename: (item: FolderItem | FileItem, isFolder: boolean) => void;
  onMove?: (file: FileItem) => void;
  onDelete: (item: FolderItem | FileItem, isFolder: boolean) => void;
}

export const ListItem: React.FC<ListItemProps> = ({
  item,
  isFolder,
  onOpenFolder,
  onDownload,
  onRename,
  onMove,
  onDelete,
}) => {
  const name = isFolder ? (item as FolderItem).name : (item as FileItem).filename;
  const size = isFolder ? null : (item as FileItem).size;
  const contentType = isFolder ? null : (item as FileItem).contentType;
  const date = item.updatedAt || item.createdAt;

  const handleRowClick = () => {
    if (isFolder && onOpenFolder) {
      onOpenFolder(item.id);
    } else if (!isFolder && onDownload) {
      onDownload(item.id, name);
    }
  };

  return (
    <div className="group flex items-center justify-between px-4 py-3 rounded-xl hover:bg-slate-800/60 border border-transparent hover:border-slate-800 transition-colors text-sm text-slate-200">
      {/* Name and Icon */}
      <div
        onClick={handleRowClick}
        className="flex items-center space-x-3 flex-1 min-w-0 cursor-pointer pr-4"
      >
        <FileIcon
          isFolder={isFolder}
          filename={name}
          contentType={contentType}
          className="w-5 h-5 flex-shrink-0"
        />
        <span
          title={name}
          className="font-medium text-slate-100 truncate group-hover:text-brand-300 transition-colors"
        >
          {name}
        </span>
      </div>

      {/* Date */}
      <div className="hidden sm:block w-36 text-xs text-slate-400 truncate">
        {formatDate(date)}
      </div>

      {/* Size */}
      <div className="hidden md:block w-24 text-right text-xs text-slate-400 font-mono">
        {isFolder ? '—' : formatBytes(size || 0)}
      </div>

      {/* Actions */}
      <div className="flex items-center space-x-1 pl-4 opacity-70 group-hover:opacity-100 transition-opacity">
        {!isFolder && onDownload && (
          <button
            onClick={() => onDownload(item.id, name)}
            title="Download"
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-700/80 transition-colors cursor-pointer"
          >
            <Download className="w-4 h-4 text-brand-400" />
          </button>
        )}

        <button
          onClick={() => onRename(item, isFolder)}
          title="Rename"
          className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-700/80 transition-colors cursor-pointer"
        >
          <Edit2 className="w-4 h-4 text-sky-400" />
        </button>

        {!isFolder && onMove && (
          <button
            onClick={() => onMove(item as FileItem)}
            title="Move to folder"
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-700/80 transition-colors cursor-pointer"
          >
            <FolderInput className="w-4 h-4 text-amber-400" />
          </button>
        )}

        <button
          onClick={() => onDelete(item, isFolder)}
          title="Delete"
          className="p-1.5 rounded-lg text-slate-400 hover:text-rose-300 hover:bg-rose-500/20 transition-colors cursor-pointer"
        >
          <Trash2 className="w-4 h-4 text-rose-400" />
        </button>
      </div>
    </div>
  );
};
