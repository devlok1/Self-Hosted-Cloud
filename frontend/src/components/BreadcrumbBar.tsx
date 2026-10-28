import React from 'react';
import { ChevronRight, Home } from 'lucide-react';
import type { Breadcrumb } from '../types';

interface BreadcrumbBarProps {
  currentFolderId: number | null;
  breadcrumbs: Breadcrumb[];
  onNavigate: (folderId: number | null) => void;
}

export const BreadcrumbBar: React.FC<BreadcrumbBarProps> = ({
  currentFolderId,
  breadcrumbs,
  onNavigate,
}) => {
  return (
    <nav className="flex items-center space-x-1.5 text-xs sm:text-sm text-slate-400 overflow-x-auto py-1">
      <button
        onClick={() => onNavigate(null)}
        className={`flex items-center space-x-1 px-2.5 py-1.5 rounded-lg transition-colors cursor-pointer ${
          currentFolderId === null
            ? 'bg-slate-800/80 text-white font-semibold'
            : 'hover:text-slate-200 hover:bg-slate-800/50'
        }`}
      >
        <Home className="w-4 h-4 text-brand-400" />
        <span>Root</span>
      </button>

      {breadcrumbs.map((crumb) => {
        const isCurrent = crumb.id === currentFolderId;

        return (
          <React.Fragment key={crumb.id}>
            <ChevronRight className="w-3.5 h-3.5 text-slate-600 flex-shrink-0" />
            <button
              onClick={() => onNavigate(crumb.id)}
              disabled={isCurrent}
              className={`px-2.5 py-1.5 rounded-lg truncate max-w-[140px] sm:max-w-[200px] transition-colors cursor-pointer ${
                isCurrent
                  ? 'bg-slate-800/80 text-white font-semibold cursor-default'
                  : 'hover:text-slate-200 hover:bg-slate-800/50'
              }`}
            >
              {crumb.name}
            </button>
          </React.Fragment>
        );
      })}
    </nav>
  );
};
