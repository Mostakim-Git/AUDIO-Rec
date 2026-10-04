package android.content;

import java.util.HashMap;
import java.util.Map;

/** In-memory stand-in with the real get/put semantics. */
public class SharedPreferences {

    public interface Editor {
        Editor putString(String key, String value);
        Editor putInt(String key, int value);
        Editor putLong(String key, long value);
        Editor putFloat(String key, float value);
        Editor putBoolean(String key, boolean value);
        Editor remove(String key);
        Editor clear();
        boolean commit();
        void apply();
    }

    private final Map<String, Object> mMap = new HashMap<>();

    public String getString(String key, String def) {
        Object v = mMap.get(key);
        return v == null ? def : String.valueOf(v);
    }

    public int getInt(String key, int def) {
        Object v = mMap.get(key);
        return v instanceof Number ? ((Number) v).intValue() : def;
    }

    public long getLong(String key, long def) {
        Object v = mMap.get(key);
        return v instanceof Number ? ((Number) v).longValue() : def;
    }

    public float getFloat(String key, float def) {
        Object v = mMap.get(key);
        return v instanceof Number ? ((Number) v).floatValue() : def;
    }

    public boolean getBoolean(String key, boolean def) {
        Object v = mMap.get(key);
        return v instanceof Boolean ? (Boolean) v : def;
    }

    public boolean contains(String key) { return mMap.containsKey(key); }

    public Editor edit() {
        return new Editor() {
            @Override public Editor putString(String key, String value) {
                mMap.put(key, value); return this;
            }
            @Override public Editor putInt(String key, int value) {
                mMap.put(key, value); return this;
            }
            @Override public Editor putLong(String key, long value) {
                mMap.put(key, value); return this;
            }
            @Override public Editor putFloat(String key, float value) {
                mMap.put(key, value); return this;
            }
            @Override public Editor putBoolean(String key, boolean value) {
                mMap.put(key, value); return this;
            }
            @Override public Editor remove(String key) { mMap.remove(key); return this; }
            @Override public Editor clear() { mMap.clear(); return this; }
            @Override public boolean commit() { return true; }
            @Override public void apply() { }
        };
    }
}
