package top.niunaijun.blackbox.fake.provider;

import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Universal outgoing-URI wrapper for "Open with" / ACTION_VIEW / shares.
 *
 * Problem:
 *  - Guest apps often emit private content:// Uris (e.g. com.xxx.filemanager.provider/...)
 *  - BlackBox starts the chooser/target under host UID, so Android denies that Uri.
 *
 * Fix:
 *  - Replace guest-owned Uris with a BlackBox-owned proxy provider Uri.
 *  - The proxy provider streams the original content (no full copy for large files).
 */
public final class FileProxyRegistry {

    private FileProxyRegistry() {}

    public static final class Entry {
        public final Uri source;
        public final String mime;
        public final String displayName;
        public final long size;
        public final long expiresAt;

        Entry(Uri source, String mime, String displayName, long size, long expiresAt) {
            this.source = source;
            this.mime = mime;
            this.displayName = displayName;
            this.size = size;
            this.expiresAt = expiresAt;
        }
    }

    private static final long TTL_MS = 5 * 60 * 1000L; // 5 minutes
    private static final ConcurrentHashMap<String, Entry> MAP = new ConcurrentHashMap<>();

    public static String getAuthority(Context ctx) {
        return ctx.getPackageName() + ".blackbox.FileProxy";
    }

    public static Entry get(String token) {
        Entry e = MAP.get(token);
        if (e == null) return null;
        if (System.currentTimeMillis() > e.expiresAt) {
            MAP.remove(token);
            return null;
        }
        return e;
    }

    public static Uri register(Context ctx, Uri source, String mimeHint) {
        final String token = UUID.randomUUID().toString().replace("-", "");
        final long expiresAt = System.currentTimeMillis() + TTL_MS;

        String mime = mimeHint;
        if (TextUtils.isEmpty(mime)) {
            try {
                mime = ctx.getContentResolver().getType(source);
            } catch (Throwable ignored) { }
        }

        String name = null;
        long size = -1;

        // Try OpenableColumns first.
        try (Cursor c = ctx.getContentResolver().query(source,
                new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE},
                null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int iName = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int iSize = c.getColumnIndex(OpenableColumns.SIZE);
                if (iName >= 0) name = c.getString(iName);
                if (iSize >= 0) size = c.getLong(iSize);
            }
        } catch (Throwable ignored) { }

        // Try AssetFileDescriptor length.
        if (size < 0) {
            try (AssetFileDescriptor afd = ctx.getContentResolver().openAssetFileDescriptor(source, "r")) {
                if (afd != null) {
                    long len = afd.getLength();
                    if (len >= 0) size = len;
                }
            } catch (Throwable ignored) { }
        }

        if (TextUtils.isEmpty(name)) {
            String last = source.getLastPathSegment();
            name = TextUtils.isEmpty(last) ? "shared.bin" : last;
        }

        if (TextUtils.isEmpty(mime)) {
            try {
                mime = guessMimeFromName(name);
            } catch (Throwable ignored) { }
        }

        MAP.put(token, new Entry(source, mime, name, size, expiresAt));

        return new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(getAuthority(ctx))
                .appendPath("t")
                .appendPath(token)
                .build();
    }

    
    private static boolean isBadMime(String t) {
        if (TextUtils.isEmpty(t)) return true;
        if ("*/*".equals(t)) return true;
        if ("application/octet-stream".equals(t)) return true;
        // Many file managers mistakenly label everything as text/plain -> breaks chooser.
        if ("text/plain".equals(t)) return true;
        if (t.startsWith("text/")) return true;
        return false;
    }

    private static String guessMimeFromName(String name) {
        if (TextUtils.isEmpty(name)) return null;
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot + 1 >= name.length()) return null;
        String ext = name.substring(dot + 1).toLowerCase();
        if (TextUtils.isEmpty(ext)) return null;
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
    }

    private static String resolveMime(Context ctx, Intent intent, Uri source, String displayName) {
        String t = intent != null ? intent.getType() : null;
        if (!isBadMime(t)) return t;

        // Best: provider-reported type.
        try {
            String cr = ctx.getContentResolver().getType(source);
            if (!TextUtils.isEmpty(cr)) return cr;
        } catch (Throwable ignored) { }

        // Fallback: extension guess.
        String guessed = guessMimeFromName(displayName);
        if (!TextUtils.isEmpty(guessed)) return guessed;

        // Last resort.
        return "application/octet-stream";
    }

/**
     * Wrap an outgoing intent so ALL content:// Uris become BlackBox-owned proxy Uris.
     * Also forces proper grant flags + ClipData.
     */
    public static void wrapOutgoingIntent(Context ctx, Intent intent) {
        if (ctx == null || intent == null) return;

        final int GRANT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;

        String hostFileProvider = ctx.getPackageName() + ".blackbox.FileProvider";
        String hostProxyProvider = getAuthority(ctx);

        Uri data = intent.getData();
        Uri originalData = data;
        if (data != null && "content".equals(data.getScheme())) {
            Uri out = maybeWrapUri(ctx, data, intent.getType(), hostFileProvider, hostProxyProvider);
            if (out != null && !out.equals(data)) {
                data = out;
            }

            // Force correct MIME so the chooser shows the right apps (video players, pdf viewers, etc).
            String displayName = null;
            try {
                Cursor c = ctx.getContentResolver().query(originalData,
                        new String[]{OpenableColumns.DISPLAY_NAME},
                        null, null, null);
                if (c != null) {
                    try {
                        if (c.moveToFirst()) {
                            int iName = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                            if (iName >= 0) displayName = c.getString(iName);
                        }
                    } finally {
                        c.close();
                    }
                }
            } catch (Throwable ignored) { }

            String mime = resolveMime(ctx, intent, originalData != null ? originalData : data, displayName);

            String action = intent.getAction();
            if (Intent.ACTION_VIEW.equals(action) || Intent.ACTION_EDIT.equals(action)) {
                intent.setDataAndType(data, mime);
            } else if (Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) {
                intent.setType(mime);
                intent.setData(data);
            } else {
                intent.setDataAndType(data, mime);
            }

            intent.addFlags(GRANT_FLAGS);

            // Ensure ClipData is present (some versions enforce grants more reliably with ClipData).
            if (intent.getClipData() == null) {
                intent.setClipData(ClipData.newUri(ctx.getContentResolver(), "data", data));
            }
        }

        ClipData clip = intent.getClipData();
        if (clip != null && clip.getItemCount() > 0) {
            boolean changed = false;
            ClipData newClip = null;
            for (int i = 0; i < clip.getItemCount(); i++) {
                ClipData.Item item = clip.getItemAt(i);
                Uri u = item != null ? item.getUri() : null;
                Uri out = u;

                if (u != null && "content".equals(u.getScheme())) {
                    Uri wrapped = maybeWrapUri(ctx, u, intent.getType(), hostFileProvider, hostProxyProvider);
                    if (wrapped != null && !wrapped.equals(u)) {
                        out = wrapped;
                        changed = true;
                    }
                }

                if (newClip == null) {
                    newClip = new ClipData(clip.getDescription(), new ClipData.Item(out));
                } else {
                    newClip.addItem(new ClipData.Item(out));
                }
            }

            if (changed && newClip != null) {
                intent.setClipData(newClip);
                intent.addFlags(GRANT_FLAGS);

                // Ensure chooser MIME is sensible (some apps otherwise show only text handlers).
                if (isBadMime(intent.getType())) {
                    try {
                        ClipData.Item it0 = newClip.getItemAt(0);
                        Uri u0 = it0 != null ? it0.getUri() : null;
                        String token = FileProxyProvider.getTokenFromAny(u0);
                        FileProxyRegistry.Entry e0 = FileProxyRegistry.get(token);
                        if (e0 != null && !TextUtils.isEmpty(e0.mime)) {
                            intent.setType(e0.mime);
                        }
                    } catch (Throwable ignored) { }
                }
            }
        }
    }

    private static Uri maybeWrapUri(Context ctx, Uri in, String mimeHint,
                                   String hostFileProvider, String hostProxyProvider) {
        if (in == null) return null;
        if (!"content".equals(in.getScheme())) return in;

        String auth = in.getAuthority();
        if (TextUtils.isEmpty(auth)) return in;

        // Already host-owned.
        if (auth.equals(hostFileProvider) || auth.equals(hostProxyProvider)) return in;

        // Try best-effort conversion via known FileProvider patterns (fast path for many file managers).
        try {
            Uri converted = FileProviderHandler.convertFileUri(ctx, in);
            if (converted != null) return converted;
        } catch (Throwable ignored) { }

        // Universal path: stream through our proxy provider.
        return register(ctx, in, mimeHint);
    }
}