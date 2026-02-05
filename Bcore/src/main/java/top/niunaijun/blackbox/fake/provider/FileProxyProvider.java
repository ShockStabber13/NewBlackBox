package top.niunaijun.blackbox.fake.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.ClipData;
import android.content.Intent;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.os.ProxyFileDescriptorCallback;
import android.os.storage.StorageManager;
import android.system.ErrnoException;
import android.provider.OpenableColumns;
import android.text.TextUtils;

import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Host-owned streaming proxy provider for guest content:// Uris.
 *
 * Authority: ${applicationId}.blackbox.FileProxy
 * Paths:
 *   content://.../t/<token>
 */
public class FileProxyProvider extends ContentProvider {

    /**
     * Extract the proxy token from any Uri that points to this provider.
     * This is used by wrappers that need to detect if an Intent already carries a FileProxy Uri.
     */
    public static String getTokenFromAny(Uri uri) {
        if (uri == null) return null;
        try {
            // We accept only our known path format: /t/<token>
            if (MATCHER.match(uri) != MATCH_TOKEN) return null;
            java.util.List<String> seg = uri.getPathSegments();
            if (seg.size() >= 2 && "t".equals(seg.get(0))) {
                return seg.get(1);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }


    private static final int MATCH_TOKEN = 1;
    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);


    /**
     * Returns the authority of this proxy provider for the host package.
     */
    public static String getAuthority(Context context) {
        if (context == null) return null;
        return context.getPackageName() + ".blackbox.FileProxy";
    }

    /**
     * Checks whether the given Uri points to this FileProxyProvider.
     */
    public static boolean isProxyUri(Context context, Uri uri) {
        if (context == null || uri == null) return false;
        if (!"content".equals(uri.getScheme())) return false;
        String auth = getAuthority(context);
        return auth != null && auth.equals(uri.getAuthority());
    }

    /**
     * Checks whether the intent is carrying our FileProxyProvider uri (data or ClipData).
     */
    public static boolean isProxyIntent(Context context, Intent intent) {
        if (intent == null) return false;
        Uri d = intent.getData();
        if (isProxyUri(context, d)) return true;
        ClipData cd = intent.getClipData();
        if (cd != null) {
            for (int i = 0; i < cd.getItemCount(); i++) {
                ClipData.Item it = cd.getItemAt(i);
                Uri u = it != null ? it.getUri() : null;
                if (isProxyUri(context, u)) return true;
            }
        }
        // ACTION_SEND often uses EXTRA_STREAM
        try {
            Uri stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (isProxyUri(context, stream)) return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private HandlerThread mThread;
    private Handler mHandler;

    @Override
    public boolean onCreate() {
        String authority = FileProxyRegistry.getAuthority(getContext());
        MATCHER.addURI(authority, "t/*", MATCH_TOKEN);

        mThread = new HandlerThread("bb-file-proxy");
        mThread.start();
        mHandler = new Handler(mThread.getLooper());
        return true;
    }

    @Override
    public String getType(Uri uri) {
        String token = getToken(uri);
        FileProxyRegistry.Entry e = FileProxyRegistry.get(token);
        if (e == null) return null;
        return e.mime;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return query(uri, projection, selection, selectionArgs, sortOrder, null);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder, CancellationSignal cancellationSignal) {
        String token = getToken(uri);
        FileProxyRegistry.Entry e = FileProxyRegistry.get(token);
        if (e == null) return null;

        String[] cols = projection;
        if (cols == null || cols.length == 0) {
            cols = new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        }

        MatrixCursor c = new MatrixCursor(cols, 1);
        MatrixCursor.RowBuilder row = c.newRow();
        for (String col : cols) {
            if (OpenableColumns.DISPLAY_NAME.equals(col)) {
                row.add(e.displayName);
            } else if (OpenableColumns.SIZE.equals(col)) {
                row.add(e.size >= 0 ? e.size : null);
            } else {
                row.add(null);
            }
        }
        return c;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        Context ctx = getContext();
        if (ctx == null) throw new FileNotFoundException("no context");
        if (!TextUtils.equals(mode, "r") && !TextUtils.equals(mode, "rw") && !TextUtils.equals(mode, "rwt")) {
            // We only guarantee read streaming.
            mode = "r";
        }

        String token = getToken(uri);
        FileProxyRegistry.Entry e = FileProxyRegistry.get(token);
        if (e == null) throw new FileNotFoundException("expired or unknown token");

        if (Build.VERSION.SDK_INT >= 26) {
            StorageManager sm = (StorageManager) ctx.getSystemService(Context.STORAGE_SERVICE);
            if (sm == null) throw new FileNotFoundException("no StorageManager");
            int pfdMode = ParcelFileDescriptor.MODE_READ_ONLY;
            try {
                return sm.openProxyFileDescriptor(pfdMode, new StreamCallback(ctx, e), mHandler);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }

        // Pre-O fallback: best-effort pipe stream (no reliable seeking).
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            ParcelFileDescriptor readSide = pipe[0];
            ParcelFileDescriptor writeSide = pipe[1];
            mHandler.post(() -> {
                try (InputStream in = ctx.getContentResolver().openInputStream(e.source);
                     ParcelFileDescriptor.AutoCloseOutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(writeSide)) {
                    if (in == null) return;
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) >= 0) {
                        out.write(buf, 0, n);
                    }
                    out.flush();
                } catch (Throwable ignored) {
                }
            });
            return readSide;
        } catch (IOException io) {
            throw new FileNotFoundException(io.toString());
        }
    }

    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }

    private String getToken(Uri uri) {
        if (uri == null) return null;
        if (MATCHER.match(uri) != MATCH_TOKEN) return null;
        try {
            return uri.getPathSegments().get(1); // ["t", "<token>"]
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static final class StreamCallback extends ProxyFileDescriptorCallback {
        private final Context ctx;
        private final FileProxyRegistry.Entry entry;

        private volatile long size = -1;

        // Fast path when the resolver can provide a real FD (supports seeking well).
        private FileChannel channel;
        private ParcelFileDescriptor basePfd;

        StreamCallback(Context ctx, FileProxyRegistry.Entry entry) {
            this.ctx = ctx.getApplicationContext();
            this.entry = entry;
            initChannel();
        }

        private void initChannel() {
            try {
                android.content.res.AssetFileDescriptor afd =
                        ctx.getContentResolver().openAssetFileDescriptor(entry.source, "r");
                if (afd != null) {
                    basePfd = afd.getParcelFileDescriptor();
                    if (basePfd != null) {
                        channel = new FileInputStream(basePfd.getFileDescriptor()).getChannel();
                    }
                    long len = afd.getLength();
                    if (len >= 0) size = len;
                }
            } catch (Throwable ignored) {
            }
            if (size < 0 && entry.size >= 0) size = entry.size;
        }

        @Override
        public long onGetSize() {
            return size >= 0 ? size : 0;
        }

        @Override
        public int onRead(long offset, int count, byte[] data) throws ErrnoException {
            if (data == null || count <= 0) return 0;
            int toRead = Math.min(count, data.length);

            // Try random-access file channel first.
            FileChannel ch = channel;
            if (ch != null) {
                try {
                    ByteBuffer buf = ByteBuffer.wrap(data, 0, toRead);
                    ch.position(offset);
                    int n = ch.read(buf);
                    return n < 0 ? 0 : n;
                } catch (IOException ignored) {
                    // Fall back to stream below.
                }
            }

            // Universal fallback: reopen stream and skip to offset.
            try (InputStream in = ctx.getContentResolver().openInputStream(entry.source)) {
                if (in == null) return 0;
                long skipped = 0;
                while (skipped < offset) {
                    long s = in.skip(offset - skipped);
                    if (s <= 0) break;
                    skipped += s;
                }
                int n = in.read(data, 0, toRead);
                return n < 0 ? 0 : n;
            } catch (IOException io) {
                throw new ErrnoException("read", android.system.OsConstants.EIO);
            }
        }

        @Override
        public void onRelease() {
            try {
                if (channel != null) channel.close();
            } catch (Throwable ignored) { }
            try {
                if (basePfd != null) basePfd.close();
            } catch (Throwable ignored) { }
            channel = null;
            basePfd = null;
        }
    }
}