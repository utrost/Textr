package de.utrost.textr;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Runs in the separate test APK process, without the target APK's Kotlin runtime. */
public class FixtureProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override public String getType(Uri uri) {
        String name = uri.getLastPathSegment();
        if ("sample.json".equals(name)) return "application/json";
        if ("sample.md".equals(name)) return "text/markdown";
        return "text/plain";
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        MatrixCursor cursor = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        cursor.addRow(new Object[]{uri.getLastPathSegment(), null});
        return cursor;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new AssertionError("Textr must never request writes");
        String name = uri.getLastPathSegment();
        if ("missing.txt".equals(name)) throw new FileNotFoundException();
        final byte[] bytes;
        if ("sample.json".equals(name)) {
            bytes = "{\"message\":\"Textr fixture\",\"value\":42}".getBytes(StandardCharsets.UTF_8);
        } else if ("sample.md".equals(name)) {
            bytes = ("# Textr fixture\n\n| Name | Value |\n| --- | --- |\n| First | 42 |\n\n"
                + "![remote](https://example.com/pixel)\n").getBytes(StandardCharsets.UTF_8);
        } else if ("binary.txt".equals(name)) {
            bytes = new byte[]{0, 1, 2};
        } else if ("oversized.txt".equals(name) || "boundary.txt".equals(name)) {
            bytes = new byte[5 * 1024 * 1024 + ("oversized.txt".equals(name) ? 1 : 0)];
            Arrays.fill(bytes, (byte) 'a');
            byte[] tail = "\nBoundary end marker".getBytes(StandardCharsets.UTF_8);
            System.arraycopy(tail, 0, bytes, bytes.length - tail.length, tail.length);
        } else {
            bytes = "Textr fixture\nRead-only text.".getBytes(StandardCharsets.UTF_8);
        }
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            new Thread(() -> {
                try (OutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    output.write(bytes);
                } catch (IOException ignored) { /* The reader may stop at its byte limit. */ }
            }).start();
            return pipe[0];
        } catch (IOException error) {
            throw new FileNotFoundException(error.getMessage());
        }
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new AssertionError("Read-only fixture"); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new AssertionError("Read-only fixture"); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new AssertionError("Read-only fixture"); }
}
