package de.utrost.textr;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.concurrent.ConcurrentHashMap;

/** Synthetic output documents only. "source" is immutable; failures never touch real files. */
public class OutputFixtureProvider extends DocumentsProvider {
    private final ConcurrentHashMap<String, String> modes = new ConcurrentHashMap<>();
    private final java.util.List<ParcelFileDescriptor> stalled = new java.util.concurrent.CopyOnWriteArrayList<>();
    private File directory() { File root = new File(getContext().getCacheDir(), "output-fixtures"); root.mkdirs(); return root; }
    private File file(String id) throws FileNotFoundException {
        if (!id.matches("[a-zA-Z0-9_.-]+")) throw new FileNotFoundException();
        return new File(directory(), id);
    }
    @Override public boolean onCreate() { return true; }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        if ("configure".equals(method)) {
            modes.put(arg, extras.getString("mode", "normal"));
            try { java.nio.file.Files.write(file(arg).toPath(), extras.getByteArray("bytes") != null ? extras.getByteArray("bytes") : new byte[0]); }
            catch (Exception error) { throw new IllegalStateException(error); }
            return Bundle.EMPTY;
        }
        if ("inspect".equals(method)) {
            Bundle result = new Bundle();
            try { result.putBoolean("exists", file(arg).exists()); if (file(arg).exists()) result.putByteArray("bytes", java.nio.file.Files.readAllBytes(file(arg).toPath())); }
            catch (Exception error) { throw new IllegalStateException(error); }
            return result;
        }
        if ("reset".equals(method)) {
            for (ParcelFileDescriptor descriptor : stalled) try { descriptor.close(); } catch (Exception ignored) { }
            stalled.clear(); modes.clear();
            for (File file : directory().listFiles()) file.delete();
            return Bundle.EMPTY;
        }
        return super.call(method, arg, extras);
    }
    @Override public Cursor queryRoots(String[] projection) {
        MatrixCursor cursor = new MatrixCursor(new String[]{DocumentsContract.Root.COLUMN_ROOT_ID, DocumentsContract.Root.COLUMN_DOCUMENT_ID, DocumentsContract.Root.COLUMN_TITLE, DocumentsContract.Root.COLUMN_FLAGS});
        cursor.addRow(new Object[]{"fixtures", "root", "Textr output fixtures", DocumentsContract.Root.FLAG_SUPPORTS_CREATE}); return cursor;
    }
    @Override public Cursor queryDocument(String id, String[] projection) throws FileNotFoundException {
        MatrixCursor cursor = new MatrixCursor(new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_FLAGS});
        cursor.addRow(new Object[]{id, id, "text/plain", file(id).length(), DocumentsContract.Document.FLAG_SUPPORTS_WRITE | DocumentsContract.Document.FLAG_SUPPORTS_DELETE}); return cursor;
    }
    @Override public Cursor queryChildDocuments(String parent, String[] projection, String order) { return new MatrixCursor(new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID}); }
    @Override public String createDocument(String parent, String mime, String name) throws FileNotFoundException {
        try { file(name).createNewFile(); } catch (Exception error) { throw new FileNotFoundException(); } return name;
    }
    @Override public void deleteDocument(String id) throws FileNotFoundException {
        if (id.equals("source") || modes.getOrDefault(id, "normal").contains("keep")) throw new FileNotFoundException("Delete unsupported");
        if (modes.getOrDefault(id, "normal").equals("slow-delete")) try { Thread.sleep(10_000); } catch (InterruptedException ignored) { }
        if (file(id).exists() && !file(id).delete()) throw new FileNotFoundException();
    }
    @Override public ParcelFileDescriptor openDocument(String id, String mode, CancellationSignal cancellation) throws FileNotFoundException {
        if (mode.equals("r")) {
            if (modes.getOrDefault(id, "normal").equals("read-denied")) throw new SecurityException("Synthetic saved file read denied");
            return ParcelFileDescriptor.open(file(id), ParcelFileDescriptor.MODE_READ_ONLY);
        }
        if (id.equals("source")) throw new AssertionError("Original must never be opened for writing");
        String behavior = modes.getOrDefault(id, "normal");
        if (behavior.startsWith("denied")) throw new SecurityException("Synthetic denied output");
        try {
            if (behavior.startsWith("stalled")) {
                ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createReliablePipe(); stalled.add(pipe[0]); return pipe[1];
            }
            if (behavior.startsWith("broken")) {
                ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createReliablePipe(); pipe[0].closeWithError("Synthetic full storage/provider failure"); return pipe[1];
            }
            return ParcelFileDescriptor.open(file(id), ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_TRUNCATE);
        } catch (java.io.IOException error) { throw new FileNotFoundException(error.getMessage()); }
    }
}
