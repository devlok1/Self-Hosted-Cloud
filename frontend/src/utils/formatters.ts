export const formatBytes = (bytes?: number | null, decimals = 2): string => {
  if (bytes === undefined || bytes === null || isNaN(bytes) || bytes <= 0) {
    return '0 B';
  }
  const k = 1024;
  const dm = decimals < 0 ? 0 : decimals;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB', 'PB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  const safeI = Math.min(i, sizes.length - 1);
  const val = (bytes / Math.pow(k, safeI)).toFixed(dm);
  return `${val} ${sizes[safeI]}`;
};

export const formatDate = (dateStr?: string | null): string => {
  if (!dateStr) return '';
  const date = new Date(dateStr);
  if (isNaN(date.getTime())) return dateStr;

  return new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  }).format(date);
};

export type FileCategory =
  | 'image'
  | 'video'
  | 'audio'
  | 'pdf'
  | 'document'
  | 'code'
  | 'archive'
  | 'generic';

export const getFileCategory = (filename: string, contentType?: string | null): FileCategory => {
  const ext = filename?.split('.').pop()?.toLowerCase() || '';
  const mime = contentType?.toLowerCase() || '';

  if (mime.startsWith('image/') || ['jpg', 'jpeg', 'png', 'gif', 'svg', 'webp', 'bmp'].includes(ext)) {
    return 'image';
  }
  if (mime.startsWith('video/') || ['mp4', 'mkv', 'webm', 'mov', 'avi'].includes(ext)) {
    return 'video';
  }
  if (mime.startsWith('audio/') || ['mp3', 'wav', 'ogg', 'm4a', 'flac'].includes(ext)) {
    return 'audio';
  }
  if (mime.includes('pdf') || ext === 'pdf') {
    return 'pdf';
  }
  if (['doc', 'docx', 'txt', 'rtf', 'odt', 'xls', 'xlsx', 'csv', 'ppt', 'pptx'].includes(ext)) {
    return 'document';
  }
  if (['zip', 'rar', 'tar', 'gz', '7z', 'bz2'].includes(ext)) {
    return 'archive';
  }
  if (['js', 'ts', 'jsx', 'tsx', 'html', 'css', 'json', 'java', 'py', 'sh', 'sql', 'xml', 'yml', 'yaml'].includes(ext)) {
    return 'code';
  }
  return 'generic';
};
