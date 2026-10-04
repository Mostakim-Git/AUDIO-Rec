package android.content;

import java.util.LinkedHashMap;
import java.util.Map;

public class ContentValues {
    private final Map<String, Object> mMap = new LinkedHashMap<>();

    public void put(String key, String value) { mMap.put(key, value); }
    public void put(String key, Integer value) { mMap.put(key, value); }
    public void put(String key, Long value) { mMap.put(key, value); }
    public void put(String key, Float value) { mMap.put(key, value); }
    public void put(String key, Double value) { mMap.put(key, value); }
    public void put(String key, Boolean value) { mMap.put(key, value); }
    public void put(String key, byte[] value) { mMap.put(key, value); }

    public Object get(String key) { return mMap.get(key); }
    public boolean containsKey(String key) { return mMap.containsKey(key); }
    public int size() { return mMap.size(); }
    public Map<String, Object> asMap() { return mMap; }

    public String getAsString(String key) {
        Object v = mMap.get(key);
        return v == null ? null : String.valueOf(v);
    }

    public Long getAsLong(String key) {
        Object v = mMap.get(key);
        return v instanceof Number ? ((Number) v).longValue() : null;
    }

    public Integer getAsInteger(String key) {
        Object v = mMap.get(key);
        return v instanceof Number ? ((Number) v).intValue() : null;
    }
}
