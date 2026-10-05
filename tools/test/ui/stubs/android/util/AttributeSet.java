package android.util;

/** Only the attribute reads the custom views do (they are never inflated here). */
public interface AttributeSet {
    boolean getAttributeBooleanValue(String namespace, String attribute, boolean defaultValue);

    int getAttributeIntValue(String namespace, String attribute, int defaultValue);

    float getAttributeFloatValue(String namespace, String attribute, float defaultValue);

    String getAttributeValue(String namespace, String attribute);

    int getAttributeCount();

    String getAttributeName(int index);

    String getAttributeValue(int index);
}
