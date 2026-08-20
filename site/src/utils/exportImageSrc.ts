const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';

/**
 * Resolve an image URL for html2canvas export.
 * Same-origin and data URLs pass through; remote CDN logos go via the API proxy
 * so the canvas is not tainted by missing CORS headers.
 */
export function exportableImageSrc(url: string | null | undefined): string | null {
  if (!url) return null;
  const trimmed = url.trim();
  if (!trimmed) return null;
  if (trimmed.startsWith('data:') || trimmed.startsWith('blob:')) return trimmed;
  if (trimmed.startsWith('/')) return trimmed;

  try {
    const parsed = new URL(trimmed, window.location.origin);
    if (parsed.origin === window.location.origin) {
      return parsed.pathname + parsed.search;
    }
  } catch {
    return null;
  }

  const base = API_BASE_URL.replace(/\/$/, '');
  return `${base}/media/image?url=${encodeURIComponent(trimmed)}`;
}

/** Load an image URL as a data URL so html2canvas always embeds pixels. */
export async function loadImageAsDataUrl(url: string): Promise<string | null> {
  try {
    const response = await fetch(url, { credentials: 'include' });
    if (!response.ok) return null;
    const blob = await response.blob();
    if (blob.size === 0) return null;
    return await blobToDataUrl(blob);
  } catch {
    return null;
  }
}

function blobToDataUrl(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onloadend = () => {
      if (typeof reader.result === 'string') {
        resolve(reader.result);
      } else {
        reject(new Error('Failed to read image'));
      }
    };
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(blob);
  });
}
