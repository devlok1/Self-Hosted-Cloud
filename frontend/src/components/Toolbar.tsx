import React from 'react';
import {
  Search,
  X,
  Upload,
  FolderPlus,
  LayoutGrid,
  List as ListIcon,
  RefreshCw,
  ArrowUpDown,
} from 'lucide-react';
import type { ViewMode, SortField, SortOrder } from '../types';

interface ToolbarProps {
  searchQuery: string;
  onSearchChange: (q: string) => void;
  onUploadClick: () => void;
  onNewFolderClick: () => void;
  viewMode: ViewMode;
  onViewModeChange: (mode: ViewMode) => void;
  sortField: SortField;
  onSortFieldChange: (field: SortField) => void;
  sortOrder: SortOrder;
  onSortOrderToggle: () => void;
  onRefresh: () => void;
  isRefreshing: boolean;
}

export const Toolbar: React.FC<ToolbarProps> = ({
  searchQuery,
  onSearchChange,
  onUploadClick,
  onNewFolderClick,
  viewMode,
  onViewModeChange,
  sortField,
  onSortFieldChange,
  sortOrder,
  onSortOrderToggle,
  onRefresh,
  isRefreshing,
}) => {
  return (
    <div className="flex flex-col md:flex-row md:items-center justify-between gap-3 py-3">
      {/* Search Input */}
      <div className="relative flex-1 max-w-md">
        <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => onSearchChange(e.target.value)}
          placeholder="Search files in your cloud..."
          className="w-full pl-10 pr-9 py-2 rounded-xl bg-slate-900/80 border border-slate-800 text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500 transition-all"
        />
        {searchQuery && (
          <button
            onClick={() => onSearchChange('')}
            className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-300"
          >
            <X className="w-4 h-4" />
          </button>
        )}
      </div>

      {/* Action buttons & controls */}
      <div className="flex items-center flex-wrap gap-2">
        {/* Sort controls */}
        <div className="flex items-center bg-slate-900/80 border border-slate-800 rounded-xl p-1 text-xs text-slate-300">
          <select
            value={sortField}
            onChange={(e) => onSortFieldChange(e.target.value as SortField)}
            className="bg-transparent text-slate-300 text-xs py-1 px-2 focus:outline-none cursor-pointer"
          >
            <option value="name" className="bg-slate-900 text-slate-200">Sort by Name</option>
            <option value="date" className="bg-slate-900 text-slate-200">Sort by Date</option>
            <option value="size" className="bg-slate-900 text-slate-200">Sort by Size</option>
          </select>
          <button
            onClick={onSortOrderToggle}
            title={sortOrder === 'asc' ? 'Ascending' : 'Descending'}
            className="p-1 hover:text-white rounded transition-colors"
          >
            <ArrowUpDown className="w-3.5 h-3.5" />
          </button>
        </div>

        {/* View Mode Switcher */}
        <div className="flex items-center bg-slate-900/80 border border-slate-800 rounded-xl p-1">
          <button
            onClick={() => onViewModeChange('grid')}
            title="Grid view"
            className={`p-1.5 rounded-lg transition-colors ${
              viewMode === 'grid'
                ? 'bg-slate-800 text-brand-400 font-semibold'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <LayoutGrid className="w-4 h-4" />
          </button>
          <button
            onClick={() => onViewModeChange('list')}
            title="List view"
            className={`p-1.5 rounded-lg transition-colors ${
              viewMode === 'list'
                ? 'bg-slate-800 text-brand-400 font-semibold'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <ListIcon className="w-4 h-4" />
          </button>
        </div>

        {/* Refresh */}
        <button
          onClick={onRefresh}
          disabled={isRefreshing}
          title="Refresh files"
          className="p-2 rounded-xl bg-slate-900/80 border border-slate-800 text-slate-400 hover:text-slate-200 hover:bg-slate-800 transition-colors cursor-pointer"
        >
          <RefreshCw className={`w-4 h-4 ${isRefreshing ? 'animate-spin text-brand-400' : ''}`} />
        </button>

        {/* New Folder */}
        <button
          onClick={onNewFolderClick}
          className="flex items-center space-x-1.5 px-3 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs sm:text-sm font-medium border border-slate-700 transition-colors cursor-pointer"
        >
          <FolderPlus className="w-4 h-4 text-amber-400" />
          <span>New Folder</span>
        </button>

        {/* Upload File */}
        <button
          onClick={onUploadClick}
          className="flex items-center space-x-1.5 px-3.5 py-2 rounded-xl bg-gradient-to-r from-brand-600 to-sky-500 hover:from-brand-500 hover:to-sky-400 text-white text-xs sm:text-sm font-semibold shadow-md shadow-brand-500/20 transition-all cursor-pointer"
        >
          <Upload className="w-4 h-4" />
          <span>Upload</span>
        </button>
      </div>
    </div>
  );
};
