import React, { useState, useRef } from 'react';
import { X, UploadCloud, File as FileIcon, CheckCircle2, AlertCircle } from 'lucide-react';
import { filesApi, parseApiError } from '../api/client';
import { formatBytes } from '../utils/formatters';

interface UploadModalProps {
  isOpen: boolean;
  onClose: () => void;
  folderId: number | null;
  folderName: string;
  onUploadComplete: () => void;
}

export const UploadModal: React.FC<UploadModalProps> = ({
  isOpen,
  onClose,
  folderId,
  folderName,
  onUploadComplete,
}) => {
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [isDragging, setIsDragging] = useState(false);
  const [uploadProgress, setUploadProgress] = useState<number | null>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  const fileInputRef = useRef<HTMLInputElement>(null);

  if (!isOpen) return null;

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(true);
  };

  const handleDragLeave = () => {
    setIsDragging(false);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      setSelectedFile(e.dataTransfer.files[0]);
      setError(null);
      setSuccess(false);
    }
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      setSelectedFile(e.target.files[0]);
      setError(null);
      setSuccess(false);
    }
  };

  const startUpload = async () => {
    if (!selectedFile) return;

    setUploading(true);
    setUploadProgress(0);
    setError(null);

    try {
      await filesApi.upload(selectedFile, folderId, (percent) => {
        setUploadProgress(percent);
      });

      setSuccess(true);
      setUploadProgress(100);
      onUploadComplete();

      setTimeout(() => {
        handleClose();
      }, 1200);
    } catch (err) {
      setError(parseApiError(err));
      setUploadProgress(null);
    } finally {
      setUploading(false);
    }
  };

  const handleClose = () => {
    if (uploading) return;
    setSelectedFile(null);
    setUploadProgress(null);
    setError(null);
    setSuccess(false);
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-fadeIn">
      <div className="glass-modal w-full max-w-lg rounded-2xl p-6 shadow-2xl shadow-black relative">
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-slate-800">
          <div>
            <h3 className="text-lg font-bold text-white">Upload File</h3>
            <p className="text-xs text-slate-400 mt-0.5">
              Uploading to: <strong className="text-brand-400">{folderName}</strong>
            </p>
          </div>
          <button
            onClick={handleClose}
            disabled={uploading}
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 disabled:opacity-50 cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Drop zone */}
        <div className="mt-5">
          {!selectedFile ? (
            <div
              onDragOver={handleDragOver}
              onDragLeave={handleDragLeave}
              onDrop={handleDrop}
              onClick={() => fileInputRef.current?.click()}
              className={`border-2 border-dashed rounded-2xl p-8 text-center cursor-pointer transition-all ${
                isDragging
                  ? 'border-brand-500 bg-brand-500/10'
                  : 'border-slate-700/80 hover:border-slate-600 bg-slate-900/50 hover:bg-slate-900/80'
              }`}
            >
              <input
                ref={fileInputRef}
                type="file"
                onChange={handleFileChange}
                className="hidden"
              />
              <div className="w-12 h-12 rounded-xl bg-slate-800 flex items-center justify-center mx-auto mb-3 text-brand-400">
                <UploadCloud className="w-6 h-6" />
              </div>
              <p className="text-sm font-semibold text-white">Click or drag & drop a file here</p>
              <p className="text-xs text-slate-400 mt-1">Upload limit up to 1 GB</p>
            </div>
          ) : (
            <div className="p-4 rounded-xl bg-slate-900/80 border border-slate-800">
              <div className="flex items-center space-x-3">
                <div className="w-10 h-10 rounded-lg bg-slate-800 flex items-center justify-center text-brand-400 flex-shrink-0">
                  <FileIcon className="w-5 h-5" />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-white truncate">{selectedFile.name}</p>
                  <p className="text-xs text-slate-400">{formatBytes(selectedFile.size)}</p>
                </div>
                {!uploading && !success && (
                  <button
                    onClick={() => setSelectedFile(null)}
                    className="p-1.5 text-slate-400 hover:text-white cursor-pointer"
                  >
                    <X className="w-4 h-4" />
                  </button>
                )}
              </div>

              {/* Progress bar */}
              {uploadProgress !== null && (
                <div className="mt-4">
                  <div className="flex justify-between text-xs text-slate-300 mb-1.5 font-mono">
                    <span>{success ? 'Upload Complete' : 'Uploading...'}</span>
                    <span>{uploadProgress}%</span>
                  </div>
                  <div className="w-full h-2 rounded-full bg-slate-800 overflow-hidden">
                    <div
                      className={`h-full transition-all duration-300 rounded-full ${
                        success
                          ? 'bg-emerald-500'
                          : 'bg-gradient-to-r from-brand-500 to-sky-400'
                      }`}
                      style={{ width: `${uploadProgress}%` }}
                    />
                  </div>
                </div>
              )}
            </div>
          )}
        </div>

        {/* Error message */}
        {error && (
          <div className="mt-4 p-3 rounded-xl bg-rose-500/10 border border-rose-500/20 text-rose-300 text-xs flex items-start space-x-2">
            <AlertCircle className="w-4 h-4 flex-shrink-0 mt-0.5" />
            <span>{error}</span>
          </div>
        )}

        {/* Success message */}
        {success && (
          <div className="mt-4 p-3 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-300 text-xs flex items-center space-x-2">
            <CheckCircle2 className="w-4 h-4 text-emerald-400" />
            <span>File uploaded successfully!</span>
          </div>
        )}

        {/* Action buttons */}
        <div className="mt-6 flex justify-end space-x-3">
          <button
            onClick={handleClose}
            disabled={uploading}
            className="px-4 py-2 rounded-xl text-xs sm:text-sm font-medium text-slate-300 hover:bg-slate-800 border border-slate-700/80 transition-colors disabled:opacity-50 cursor-pointer"
          >
            Cancel
          </button>

          <button
            onClick={startUpload}
            disabled={!selectedFile || uploading || success}
            className="px-5 py-2 rounded-xl text-xs sm:text-sm font-semibold text-white bg-gradient-to-r from-brand-600 to-sky-500 hover:from-brand-500 hover:to-sky-400 disabled:opacity-50 disabled:cursor-not-allowed shadow-lg shadow-brand-500/20 transition-all cursor-pointer"
          >
            {uploading ? 'Uploading...' : 'Start Upload'}
          </button>
        </div>
      </div>
    </div>
  );
};
