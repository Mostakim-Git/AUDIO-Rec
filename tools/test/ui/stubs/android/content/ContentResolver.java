package android.content;

import android.net.Uri;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Harness stand-in for android.content.ContentResolver.
 *
 * insert()/openOutputStream()/update()/delete() are enough to run a MediaStore
 * write off-device: rows land in a map, the stream writes into a byte array and
 * the harness can read both back to prove what the app actually published.
 */
public class ContentResolver {

    private static final Map<String, byte[]> FILES = new LinkedHashMap<>();
    private static final Map<String, ContentValues> ROWS = new LinkedHashMap<>();
    private static int sNextId = 1;

    public Uri insert(Uri collection, ContentValues values) {
        String uri = collection.toString() + "/" + (sNextId++);
        ROWS.put(uri, values);
        FILES.put(uri, new byte[0]);
        if (values != null && values.getAsString("_data") != null) {
            FILES.put(uri, new byte[0]);
        }
        return Uri.parse(uri);
    }

    public int update(Uri uri, ContentValues values, String where, String[] args) {
        ContentValues row = ROWS.get(uri.toString());
        if (row == null || values == null) return 0;
        for (Map.Entry<String, Object> e : values.asMap().entrySet()) {
            row.put(e.getKey(), String.valueOf(e.getValue()));
        }
        return 1;
    }

    public int delete(Uri uri, String where, String[] args) {
        boolean had = ROWS.remove(uri.toString()) != null;
        FILES.remove(uri.toString());
        return had ? 1 : 0;
    }

    public OutputStream openOutputStream(Uri uri) throws FileNotFoundException {
        final String key = uri.toString();
        if (!ROWS.containsKey(key)) throw new FileNotFoundException(key);
        return new ByteArrayOutputStream() {
            @Override
            public void close() throws java.io.IOException {
                super.close();
                FILES.put(key, toByteArray());
            }
        };
    }

    public java.io.InputStream openInputStream(Uri uri) throws FileNotFoundException {
        byte[] data = FILES.get(uri.toString());
        return new ByteArrayInputStream(data == null ? new byte[0] : data);
    }

    // ------------------------------------------------------- harness accessors
    public static byte[] bytesOf(Uri uri) { return FILES.get(uri.toString()); }

    public static ContentValues rowOf(Uri uri) { return ROWS.get(uri.toString()); }

    public static int rowCount() { return ROWS.size(); }

    public static void reset() { FILES.clear(); ROWS.clear(); sNextId = 1; }
}
