import React from 'react';
import { Cloud, HardDrive, LogOut, User as UserIcon } from 'lucide-react';
import type { User, StorageStats } from '../types';
import { formatBytes } from '../utils/formatters';

interface NavbarProps {
  user: User | null;
  storageStats: StorageStats | null;
  onLogout: () => void;
}

export const Navbar: React.FC<NavbarProps> = ({ user, storageStats, onLogout }) => {
  return (
    <header className="sticky top-0 z-30 border-b border-slate-800/80 glass-panel">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
        {/* Brand */}
        <div className="flex items-center space-x-3">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-brand-600 to-sky-400 flex items-center justify-center shadow-lg shadow-brand-500/20">
            <Cloud className="w-6 h-6 text-white" />
          </div>
          <div>
            <div className="flex items-center space-x-2">
              <span className="text-xl font-bold tracking-tight text-white">HomeCloud</span>
              <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                <span className="w-1.5 h-1.5 mr-1 rounded-full bg-emerald-400 animate-pulse"></span>
                LAN
              </span>
            </div>
            <p className="text-[11px] text-slate-400 font-medium -mt-0.5">Self-Hosted Storage</p>
          </div>
        </div>

        {/* Right side stats & user menu */}
        {user && (
          <div className="flex items-center space-x-4">
            {/* Storage quick meter */}
            {storageStats && (
              <div className="hidden sm:flex items-center space-x-2.5 px-3 py-1.5 rounded-lg bg-slate-900/60 border border-slate-800 text-xs text-slate-300">
                <HardDrive className="w-4 h-4 text-brand-400" />
                <span>
                  <strong className="text-white font-semibold">{formatBytes(storageStats.usedBytes)}</strong> used
                </span>
                <span className="text-slate-600">•</span>
                <span className="text-slate-400">{storageStats.fileCount} {storageStats.fileCount === 1 ? 'file' : 'files'}</span>
              </div>
            )}

            {/* User profile & Logout */}
            <div className="flex items-center space-x-3 pl-2 border-l border-slate-800">
              <div className="flex items-center space-x-2 text-sm text-slate-300">
                <div className="w-8 h-8 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center text-slate-300">
                  <UserIcon className="w-4 h-4" />
                </div>
                <span className="hidden md:inline font-medium text-slate-200">{user.email}</span>
              </div>

              <button
                onClick={onLogout}
                title="Log Out"
                className="p-2 rounded-lg text-slate-400 hover:text-rose-400 hover:bg-rose-500/10 transition-colors border border-transparent hover:border-rose-500/20"
              >
                <LogOut className="w-4 h-4" />
              </button>
            </div>
          </div>
        )}
      </div>
    </header>
  );
};
