package de.utrost.textr;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.Bundle;
import android.content.Intent;
import android.provider.OpenableColumns;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Runs in the separate test APK process, without the target APK's Kotlin runtime. */
public class FixtureProvider extends ContentProvider {
    private final java.util.Set<String> revoked = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    @Override public boolean onCreate() { return true; }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        Uri target = Uri.parse(arg);
        int read = Intent.FLAG_GRANT_READ_URI_PERMISSION;
        if ("grant".equals(method)) {
            revoked.remove(arg);
            getContext().grantUriPermission("de.utrost.textr", target, read | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        } else if ("revoke".equals(method)) {
            getContext().revokeUriPermission(target, read);
            revoked.add(arg);
        } else if ("temporary".equals(method)) {
            getContext().grantUriPermission("de.utrost.textr", target, read);
        } else throw new IllegalArgumentException(method);
        return Bundle.EMPTY;
    }

    @Override public String getType(Uri uri) {
        String name = uri.getLastPathSegment();
        if ("unsupported-metadata.txt".equals(name)) throw new UnsupportedOperationException();
        if ("invalid-metadata.txt".equals(name)) throw new IllegalArgumentException();
        if ("null-metadata.txt".equals(name)) return null;
        if ("extensionless-json".equals(name)) return "application/json";
        if ("sample.json".equals(name)) return "application/json";
        if ("sample.md".equals(name)) return "text/markdown";
        return "text/plain";
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        String name = uri.getLastPathSegment();
        if ("unsupported-metadata.txt".equals(name)) throw new UnsupportedOperationException();
        if ("invalid-metadata.txt".equals(name)) throw new IllegalArgumentException();
        if ("denied.txt".equals(name)) throw new SecurityException("Fixture access denied");
        if ("null-metadata.txt".equals(name)) return null;
        if ("missing-column.txt".equals(name)) {
            MatrixCursor missing = new MatrixCursor(new String[]{OpenableColumns.SIZE});
            missing.addRow(new Object[]{null});
            return missing;
        }
        MatrixCursor cursor = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        cursor.addRow(new Object[]{uri.getLastPathSegment(), null});
        return cursor;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new AssertionError("Textr must never request writes");
        if (revoked.contains(uri.toString())) throw new SecurityException("Fixture grant revoked");
        String name = uri.getLastPathSegment();
        if ("missing.txt".equals(name)) throw new FileNotFoundException();
        final byte[] bytes;
        if ("sample.json".equals(name) || "slow.json".equals(name) || "extensionless-json".equals(name) || "UPPER.JSON".equals(name)) {
            bytes = "{\"message\":\"Textr fixture\",\"value\":42}".getBytes(StandardCharsets.UTF_8);
        } else if ("copy.txt".equals(name)) {
            bytes = "Copyable Textr selection".getBytes(StandardCharsets.UTF_8);
        } else if ("copy-long.txt".equals(name)) {
            StringBuilder text = new StringBuilder("Copyable source ");
            while (text.length() < 1018) text.append('&');
            text.append("CrossBoundaryMarker <script>literal</script> ");
            for (int i = 0; i < 400; i++) text.append("& ");
            bytes = text.toString().getBytes(StandardCharsets.UTF_8);
        } else if ("long.ini".equals(name)) {
            StringBuilder text = new StringBuilder("[reader]\n");
            for (int i = 0; i < 300; i++) text.append("line").append(i).append(" = text\n");
            text.append("# Final line marker");
            bytes = text.toString().getBytes(StandardCharsets.UTF_8);
        } else if ("invalid.json".equals(name)) {
            bytes = "{not valid JSON}".getBytes(StandardCharsets.UTF_8);
        } else if ("repeated.txt".equals(name)) {
            bytes = "needle one\nneedle two\nneedle three".getBytes(StandardCharsets.UTF_8);
        } else if ("hostile.md".equals(name)) {
            bytes = ("# Offline fixture\n\n<script>document.body.textContent='EXECUTED'</script>\n\n"
                + "<iframe src='https://example.com/frame'></iframe>\n\n"
                + "![remote](https://example.com/image)\n\n[Open external](https://example.com/textr-validation)\n\n"
                + "[script](javascript:alert%281%29)\n[local](file:///data/local/tmp/private)\n").getBytes(StandardCharsets.UTF_8);
        } else if ("sample.md".equals(name)) {
            bytes = ("# Textr fixture\n\n| Name | Value |\n| --- | --- |\n| First | 42 |\n\n"
                + "![remote](https://example.com/pixel)\n").getBytes(StandardCharsets.UTF_8);
        } else if ("large-markdown.md".equals(name) || "escaped.txt".equals(name)) {
            bytes = new byte[5 * 1024 * 1024];
            Arrays.fill(bytes, (byte) ("escaped.txt".equals(name) ? '&' : 'a'));
            byte[] tail = "\nFixture end marker".getBytes(StandardCharsets.UTF_8);
            System.arraycopy(tail, 0, bytes, bytes.length - tail.length, tail.length);
        } else if ("deep.json".equals(name)) {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < 200; i++) text.append('[');
            text.append("\"Fixture end marker\"");
            for (int i = 0; i < 200; i++) text.append(']');
            bytes = text.toString().getBytes(StandardCharsets.UTF_8);
        } else if ("tokens.json".equals(name)) {
            StringBuilder text = new StringBuilder("[");
            while (text.length() < 5 * 1024 * 1024 - 30) text.append("0,");
            text.append("\"Fixture end marker\"]");
            bytes = text.toString().getBytes(StandardCharsets.UTF_8);
        } else if ("tables.md".equals(name)) {
            StringBuilder text = new StringBuilder("| A | B |\n|---|---|\n");
            for (int i = 0; i < 12000; i++) text.append("|a|b|\n");
            text.append("\nFixture end marker");
            bytes = text.toString().getBytes(StandardCharsets.UTF_8);
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
                    if ("slow.txt".equals(name) || "slow.json".equals(name)) {
                        try { Thread.sleep(5000); } catch (InterruptedException ignored) { }
                    }
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
