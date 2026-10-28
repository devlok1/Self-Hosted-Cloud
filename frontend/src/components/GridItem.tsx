import React, { useState, useRef, useEffect } from 'react';
import { Download, MoreVertical, Edit2, FolderInput, Trash2 } from 'lucide-react';
import type { FileItem, FolderItem } from '../types';
import { FileIcon } from './FileIcon';
import { formatBytes, formatDate } from '../utils/formatters';

interface GridItemProps {
  item: FolderItem | FileItem;
  isFolder: boolean;
  onOpenFolder?: (folderId: number) => void;
  onDownload?: (fileId: number, filename: string) => void;
  onRename: (item: FolderItem | FileItem, isFolder: boolean) => void;
  onMove?: (file: FileItem) => void;
  onDelete: (item: FolderItem | FileItem, isFolder: boolean) => void;
}

export const GridItem: React.FC<GridItemProps> = ({
  item,
  isFolder,
  onOpenFolder,
  onDownload,
  onRename,
  onMove,
  onDelete,
}) => {
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  const name = isFolder ? (item as FolderItem).name : (item as FileItem).filename;
  const size = isFolder ? null : (item as FileItem).size;
  const contentType = isFolder ? null : (item as FileItem).contentType;
  const date = item.updatedAt || item.createdAt;

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setMenuOpen(false);
      }
    };
    if (menuOpen) {
      document.addEventListener('mousedown', handleClickOutside);
    }
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [menuOpen]);

  const handleCardClick = () => {
    if (isFolder && onOpenFolder) {
      onOpenFolder(item.id);
    } else if (!isFolder && onDownload) {
      onDownload(item.id, name);
    }
  };

  return (
    <div className="group relative rounded-2xl bg-slate-900/60 hover:bg-slate-800/70 border border-slate-800/80 hover:border-slate-700 p-4 transition-all duration-200 flex flex-col justify-between hover:shadow-xl hover:shadow-black/40">
      {/* Top row with icon & menu button */}
      <div className="flex items-start justify-between">
        <div
          onClick={handleCardClick}
          className="cursor-pointer flex items-center justify-center w-12 h-12 rounded-xl bg-slate-800/90 group-hover:bg-slate-700/80 transition-colors"
        >
          <FileIcon
            isFolder={isFolder}
            filename={name}
            contentType={contentType}
            className="w-7 h-7"
          />
        </div>

        {/* Action Menu */}
        <div className="relative" ref={menuRef}>
          <button
            onClick={(e) => {
              e.stopPropagation();
              setMenuOpen(!menuOpen);
            }}
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-700/60 transition-colors opacity-80 group-hover:opacity-100 cursor-pointer"
          >
            <MoreVertical className="w-4 h-4" />
          </button>

          {menuOpen && (
            <div className="absolute right-0 top-8 z-20 w-44 rounded-xl glass-modal p-1.5 shadow-2xl shadow-black/80 text-xs text-slate-200 animate-fadeIn">
              {!isFolder && onDownload && (
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    setMenuOpen(false);
                    onDownload(item.id, name);
                  }}
                  className="w-full flex items-center space-x-2 px-3 py-2 rounded-lg hover:bg-slate-800/80 hover:text-white transition-colors cursor-pointer text-left"
                >
                  <Download className="w-3.5 h-3.5 text-brand-400" />
                  <span>Download</span>
                </button>
              )}

              <button
                onClick={(e) => {
                  e.stopPropagation();
                  setMenuOpen(false);
                  onRename(item, isFolder);
                }}
                className="w-full flex items-center space-x-2 px-3 py-2 rounded-lg hover:bg-slate-800/80 hover:text-white transition-colors cursor-pointer text-left"
              >
                <Edit2 className="w-3.5 h-3.5 text-sky-400" />
                <span>Rename</span>
              </button>

              {!isFolder && onMove && (
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    setMenuOpen(false);
                    onMove(item as FileItem);
                  }}
                  className="w-full flex items-center space-x-2 px-3 py-2 rounded-lg hover:bg-slate-800/80 hover:text-white transition-colors cursor-pointer text-left"
                >
                  <FolderInput className="w-3.5 h-3.5 text-amber-400" />
                  <span>Move to folder</span>
                </button>
              )}

              <div className="my-1 border-t border-slate-800" />

              <button
                onClick={(e) => {
                  e.stopPropagation();
                  setMenuOpen(false);
                  onDelete(item, isFolder);
                }}
                className="w-full flex items-center space-x-2 px-3 py-2 rounded-lg hover:bg-rose-500/20 text-rose-300 hover:text-rose-200 transition-colors cursor-pointer text-left"
              >
                <Trash2 className="w-3.5 h-3.5 text-rose-400" />
                <span>Delete</span>
              </button>
            </div>
          )}
        </div>
      </div>

      {/* Item info */}
      <div onClick={handleCardClick} className="cursor-pointer mt-4">
        <h4
          title={name}
          className="text-sm font-semibold text-slate-100 truncate group-hover:text-brand-300 transition-colors"
        >
          {name}
        </h4>
        <div className="flex items-center justify-between text-[11px] text-slate-400 mt-1">
          <span>{isFolder ? 'Folder' : formatBytes(size || 0)}</span>
          <span>{formatDate(date)}</span>
        </div>
      </div>
    </div>
  );
};
