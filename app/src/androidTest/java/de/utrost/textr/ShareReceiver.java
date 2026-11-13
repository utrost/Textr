package de.utrost.textr;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.content.Intent;
import java.io.InputStream;
import java.security.MessageDigest;

/** Separate APK/UID: exercises Android's real grant enforcement rather than reading as Textr. */
public class ShareReceiver extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String result;
        try {
            Uri uri = getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri == null) {
                result = "text:" + getIntent().getStringExtra(Intent.EXTRA_TEXT);
            } else {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = input.read(buffer)) != -1) digest.update(buffer, 0, n);
                }
                StringBuilder hash = new StringBuilder();
                for (byte b : digest.digest()) hash.append(String.format("%02x", b & 255));
                boolean writeDenied = false;
                try (java.io.OutputStream output = getContentResolver().openOutputStream(uri, "wa")) {
                    // Do not write even if the sender accidentally granted access.
                } catch (SecurityException denied) { writeDenied = true; }
                result = hash + ":writeDenied=" + writeDenied;
            }
        } catch (Exception error) { result = "ERROR:" + error; }
        getSharedPreferences("receiver", 0).edit().putString("result", result).commit();
        finish();
    }
}
