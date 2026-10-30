import React, { useState, useRef, useEffect, useCallback } from 'react';
import {
  X,
  UploadCloud,
  File as FileIcon,
  CheckCircle2,
  AlertCircle,
  Pause,
  Play,
  RotateCcw,
  Loader2,
} from 'lucide-react';
import { transfersApi, parseApiError } from '../api/client';
import { formatBytes } from '../utils/formatters';
import { computeBlobSha256 } from '../utils/crypto';
import type { TransferStatusResponse } from '../types';

interface UploadModalProps {
  isOpen: boolean;
  onClose: () => void;
  folderId: number | null;
  folderName: string;
  onUploadComplete: () => void;
}

const CHUNK_SIZE = 5 * 1024 * 1024; // 5 MB chunk size

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
  const [isPaused, setIsPaused] = useState(false);
  const [statusMessage, setStatusMessage] = useState<string>('');
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);
  const [activeUploadId, setActiveUploadId] = useState<string | null>(null);
  const [canResume, setCanResume] = useState(false);

  const fileInputRef = useRef<HTMLInputElement>(null);
  const isPausedRef = useRef(false);
  const isCancelledRef = useRef(false);
  const isUploadingRef = useRef(false);

  const getStorageKey = useCallback(
    (file: File) => `homecloud_transfer_${folderId ?? 'root'}_${file.name}_${file.size}`,
    [folderId]
  );

  // Check for existing session in localStorage when a file is selected
  const checkExistingSession = useCallback(
    async (file: File) => {
      const key = getStorageKey(file);
      const storedUploadId = localStorage.getItem(key);
      if (!storedUploadId) {
        setCanResume(false);
        setActiveUploadId(null);
        return;
      }

      try {
        const session: TransferStatusResponse = await transfersApi.getStatus(storedUploadId);
        if (session.status === 'COMPLETED') {
          localStorage.removeItem(key);
          setCanResume(false);
          setActiveUploadId(null);
        } else if (session.status === 'UPLOADING' || session.status === 'INITIATED') {
          setActiveUploadId(storedUploadId);
          setCanResume(true);
          const percent = Math.round((session.uploadedChunksCount / session.totalChunks) * 100);
          setUploadProgress(percent);
          setStatusMessage(
            `Resumable upload found: ${session.uploadedChunksCount} of ${session.totalChunks} chunks already uploaded.`
          );
        } else {
          // EXPIRED, FAILED, or CANCELLED
          localStorage.removeItem(key);
          setCanResume(false);
          setActiveUploadId(null);
        }
      } catch {
        localStorage.removeItem(key);
        setCanResume(false);
        setActiveUploadId(null);
      }
    },
    [getStorageKey]
  );

  useEffect(() => {
    if (selectedFile) {
      checkExistingSession(selectedFile);
    }
  }, [selectedFile, checkExistingSession]);

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
      handleFileSelected(e.dataTransfer.files[0]);
    }
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      handleFileSelected(e.target.files[0]);
    }
  };

  const handleFileSelected = (file: File) => {
    setSelectedFile(file);
    setError(null);
    setSuccess(false);
    setUploadProgress(null);
    setStatusMessage('');
  };

  const uploadLoop = async (file: File, uploadId: string, initialUploadedChunks: number[] = []) => {
    const fileSize = file.size;
    const chunkSize = Math.min(CHUNK_SIZE, fileSize);
    const totalChunks = Math.max(1, Math.ceil(fileSize / chunkSize));
    const uploadedChunksSet = new Set<number>(initialUploadedChunks);

    let uploadedBytes = 0;
    for (const chunkIdx of uploadedChunksSet) {
      const start = chunkIdx * chunkSize;
      const end = Math.min(start + chunkSize, fileSize);
      uploadedBytes += end - start;
    }

    for (let i = 0; i < totalChunks; i++) {
      if (isCancelledRef.current) {
        break;
      }
      if (isPausedRef.current) {
        setStatusMessage(`Paused at chunk ${i + 1} of ${totalChunks}`);
        setUploading(false);
        isUploadingRef.current = false;
        return;
      }

      if (uploadedChunksSet.has(i)) {
        continue;
      }

      const start = i * chunkSize;
      const end = Math.min(start + chunkSize, fileSize);
      const chunkBlob = file.slice(start, end);

      setStatusMessage(`Computing checksum for chunk ${i + 1} of ${totalChunks}...`);
      const checksum = await computeBlobSha256(chunkBlob);

      setStatusMessage(`Uploading chunk ${i + 1} of ${totalChunks}...`);

      // Retry loop for transient network interruptions
      let attempt = 0;
      const maxRetries = 3;
      let successUpload = false;

      while (attempt < maxRetries) {
        if (isCancelledRef.current || isPausedRef.current) break;
        try {
          await transfersApi.uploadChunk(uploadId, i, chunkBlob, checksum);
          uploadedChunksSet.add(i);
          uploadedBytes += chunkBlob.size;
          const currentPercent = Math.min(99, Math.round((uploadedBytes / fileSize) * 100));
          setUploadProgress(currentPercent);
          successUpload = true;
          break;
        } catch (err) {
          attempt++;
          if (attempt >= maxRetries) {
            throw err;
          }
          setStatusMessage(
            `Transient error on chunk ${i + 1}. Retrying in ${attempt}s (attempt ${attempt + 1}/${maxRetries})...`
          );
          await new Promise((res) => setTimeout(res, 1000 * attempt));
        }
      }

      if (!successUpload && (isCancelledRef.current || isPausedRef.current)) {
        return;
      }
    }

    if (isCancelledRef.current || isPausedRef.current) {
      return;
    }

    // All chunks uploaded — finalize file
    setStatusMessage('All chunks uploaded. Assembling and verifying file on server...');
    await transfersApi.complete(uploadId);

    // Clean up local transfer session reference
    const key = getStorageKey(file);
    localStorage.removeItem(key);

    setSuccess(true);
    setUploadProgress(100);
    setStatusMessage('Upload complete!');
    onUploadComplete();

    setTimeout(() => {
      handleClose();
    }, 1200);
  };

  const startUpload = async () => {
    if (!selectedFile) return;

    if (selectedFile.size <= 0) {
      setError('Cannot upload empty files (0 bytes).');
      return;
    }

    setUploading(true);
    isUploadingRef.current = true;
    setIsPaused(false);
    isPausedRef.current = false;
    isCancelledRef.current = false;
    setError(null);
    setSuccess(false);

    const key = getStorageKey(selectedFile);
    const fileSize = selectedFile.size;
    const chunkSize = Math.min(CHUNK_SIZE, fileSize);

    try {
      let uploadId = activeUploadId;
      let existingUploadedChunks: number[] = [];

      if (uploadId) {
        // Verify with server before proceeding
        try {
          const status = await transfersApi.getStatus(uploadId);
          if (status.status === 'COMPLETED') {
            localStorage.removeItem(key);
            setSuccess(true);
            setUploadProgress(100);
            setStatusMessage('File already completed on server!');
            onUploadComplete();
            setTimeout(() => handleClose(), 1000);
            return;
          }
          existingUploadedChunks = status.uploadedChunks || [];
        } catch {
          uploadId = null;
        }
      }

      if (!uploadId) {
        setStatusMessage('Initiating transfer session...');
        const session = await transfersApi.initiate({
          filename: selectedFile.name,
          fileSize: selectedFile.size,
          chunkSize,
          folderId,
          contentType: selectedFile.type || 'application/octet-stream',
        });
        uploadId = session.uploadId;
        setActiveUploadId(uploadId);
        localStorage.setItem(key, uploadId);
      }

      await uploadLoop(selectedFile, uploadId, existingUploadedChunks);
    } catch (err) {
      if (!isCancelledRef.current) {
        setError(parseApiError(err));
        setStatusMessage('Upload interrupted. You can resume at any time.');
        setCanResume(true);
      }
    } finally {
      setUploading(false);
      isUploadingRef.current = false;
    }
  };

  const handlePause = () => {
    isPausedRef.current = true;
    setIsPaused(true);
    setStatusMessage('Pausing upload after current chunk...');
  };

  const handleResume = () => {
    isPausedRef.current = false;
    setIsPaused(false);
    startUpload();
  };

  const handleCancelTransfer = async () => {
    isCancelledRef.current = true;
    isPausedRef.current = false;
    setIsPaused(false);

    if (activeUploadId) {
      try {
        await transfersApi.cancel(activeUploadId);
      } catch {
        // Ignore server error on cancel
      }
    }

    if (selectedFile) {
      localStorage.removeItem(getStorageKey(selectedFile));
    }

    setActiveUploadId(null);
    setCanResume(false);
    setUploading(false);
    isUploadingRef.current = false;
    setUploadProgress(null);
    setStatusMessage('');
    setError(null);
  };

  const handleClose = () => {
    if (uploading && !isPaused) return;
    setSelectedFile(null);
    setUploadProgress(null);
    setError(null);
    setSuccess(false);
    setIsPaused(false);
    setActiveUploadId(null);
    setCanResume(false);
    setStatusMessage('');
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-fadeIn">
      <div className="glass-modal w-full max-w-lg rounded-2xl p-6 shadow-2xl shadow-black relative border border-slate-800">
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-slate-800">
          <div>
            <h3 className="text-lg font-bold text-white">Upload File (Resumable)</h3>
            <p className="text-xs text-slate-400 mt-0.5">
              Uploading to: <strong className="text-brand-400">{folderName}</strong>
            </p>
          </div>
          <button
            onClick={handleClose}
            disabled={uploading && !isPaused}
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 disabled:opacity-50 cursor-pointer transition-colors"
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
              <p className="text-xs text-slate-400 mt-1">
                Reliable chunked transfer • Resumable on disconnect
              </p>
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
                    onClick={() => {
                      handleCancelTransfer();
                      setSelectedFile(null);
                    }}
                    className="p-1.5 text-slate-400 hover:text-white cursor-pointer"
                  >
                    <X className="w-4 h-4" />
                  </button>
                )}
              </div>

              {/* Status and Progress bar */}
              {uploadProgress !== null && (
                <div className="mt-4">
                  <div className="flex justify-between items-center text-xs text-slate-300 mb-1.5 font-mono">
                    <span className="flex items-center space-x-1.5 truncate max-w-[70%]">
                      {uploading && <Loader2 className="w-3.5 h-3.5 animate-spin text-brand-400" />}
                      <span className="truncate">{statusMessage || (success ? 'Complete' : 'Uploading...')}</span>
                    </span>
                    <span className="font-semibold">{uploadProgress}%</span>
                  </div>
                  <div className="w-full h-2.5 rounded-full bg-slate-800 overflow-hidden">
                    <div
                      className={`h-full transition-all duration-300 rounded-full ${
                        success
                          ? 'bg-emerald-500'
                          : isPaused
                          ? 'bg-amber-500'
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
            <span>File uploaded and verified successfully!</span>
          </div>
        )}

        {/* Action buttons */}
        <div className="mt-6 flex items-center justify-between">
          <div>
            {uploading && (
              <button
                onClick={handlePause}
                className="flex items-center space-x-1.5 px-3 py-1.5 rounded-xl text-xs font-medium text-amber-300 bg-amber-500/10 border border-amber-500/30 hover:bg-amber-500/20 transition-colors cursor-pointer"
              >
                <Pause className="w-3.5 h-3.5" />
                <span>Pause</span>
              </button>
            )}
            {isPaused && (
              <button
                onClick={handleResume}
                className="flex items-center space-x-1.5 px-3 py-1.5 rounded-xl text-xs font-medium text-emerald-300 bg-emerald-500/10 border border-emerald-500/30 hover:bg-emerald-500/20 transition-colors cursor-pointer"
              >
                <Play className="w-3.5 h-3.5" />
                <span>Resume</span>
              </button>
            )}
          </div>

          <div className="flex space-x-3">
            {uploading || isPaused ? (
              <button
                onClick={handleCancelTransfer}
                className="px-4 py-2 rounded-xl text-xs sm:text-sm font-medium text-rose-400 hover:bg-rose-500/10 border border-rose-500/30 transition-colors cursor-pointer"
              >
                Cancel Transfer
              </button>
            ) : (
              <button
                onClick={handleClose}
                disabled={uploading}
                className="px-4 py-2 rounded-xl text-xs sm:text-sm font-medium text-slate-300 hover:bg-slate-800 border border-slate-700/80 transition-colors disabled:opacity-50 cursor-pointer"
              >
                Close
              </button>
            )}

            {!uploading && !success && (
              <button
                onClick={startUpload}
                disabled={!selectedFile}
                className="flex items-center space-x-1.5 px-5 py-2 rounded-xl text-xs sm:text-sm font-semibold text-white bg-gradient-to-r from-brand-600 to-sky-500 hover:from-brand-500 hover:to-sky-400 disabled:opacity-50 disabled:cursor-not-allowed shadow-lg shadow-brand-500/20 transition-all cursor-pointer"
              >
                {canResume ? (
                  <>
                    <RotateCcw className="w-4 h-4" />
                    <span>Resume Upload</span>
                  </>
                ) : (
                  <span>Start Upload</span>
                )}
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
