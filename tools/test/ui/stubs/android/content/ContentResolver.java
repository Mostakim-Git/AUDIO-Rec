package android.content;

import android.net.Uri;

public class ContentResolver {
    public java.io.OutputStream openOutputStream(Uri uri) throws java.io.FileNotFoundException {
        return new java.io.ByteArrayOutputStream();
    }

    public java.io.InputStream openInputStream(Uri uri) throws java.io.FileNotFoundException {
        return new java.io.ByteArrayInputStream(new byte[0]);
    }
}
