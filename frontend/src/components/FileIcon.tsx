import React from 'react';
import {
  Folder,
  FileText,
  FileImage,
  FileVideo,
  FileAudio,
  FileCode,
  FileArchive,
  File as GenericFile,
} from 'lucide-react';
import { getFileCategory } from '../utils/formatters';

interface FileIconProps {
  isFolder?: boolean;
  filename?: string;
  contentType?: string | null;
  className?: string;
}

export const FileIcon: React.FC<FileIconProps> = ({
  isFolder = false,
  filename = '',
  contentType,
  className = 'w-6 h-6',
}) => {
  if (isFolder) {
    return <Folder className={`${className} text-amber-400 fill-amber-400/20`} />;
  }

  const category = getFileCategory(filename, contentType);

  switch (category) {
    case 'image':
      return <FileImage className={`${className} text-emerald-400`} />;
    case 'video':
      return <FileVideo className={`${className} text-rose-400`} />;
    case 'audio':
      return <FileAudio className={`${className} text-purple-400`} />;
    case 'pdf':
      return <FileText className={`${className} text-red-400`} />;
    case 'document':
      return <FileText className={`${className} text-sky-400`} />;
    case 'code':
      return <FileCode className={`${className} text-cyan-400`} />;
    case 'archive':
      return <FileArchive className={`${className} text-yellow-400`} />;
    default:
      return <GenericFile className={`${className} text-slate-400`} />;
  }
};
