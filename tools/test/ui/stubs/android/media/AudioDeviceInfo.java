package android.media;

/** Only what AudioDevice.from() reads. */
public class AudioDeviceInfo {
    public static final int TYPE_UNKNOWN = 0;
    public static final int TYPE_BUILTIN_MIC = 15;
    public static final int TYPE_BUILTIN_EARPIECE = 1;
    public static final int TYPE_BUILTIN_SPEAKER = 2;
    public static final int TYPE_USB_DEVICE = 11;
    public static final int TYPE_USB_ACCESSORY = 12;
    public static final int TYPE_USB_HEADSET = 22;
    public static final int TYPE_WIRED_HEADSET = 3;
    public static final int TYPE_WIRED_HEADPHONES = 4;
    public static final int TYPE_LINE_ANALOG = 5;
    public static final int TYPE_HDMI = 9;
    public static final int TYPE_BLUETOOTH_SCO = 7;
    public static final int TYPE_TELEPHONY = 18;
    public static final int TYPE_AUX_LINE = 19;
    public static final int TYPE_IP = 20;
    public static final int TYPE_BUS = 21;
    public static final int TYPE_BLUETOOTH_A2DP = 8;
    public static final int TYPE_BLUETOOTH_A2DP_HEADPHONES = 6;
    public static final int TYPE_BLUETOOTH_A2DP_SPEAKER = 23;
    public static final int TYPE_HDMI_ARC = 10;
    public static final int TYPE_HDMI_EARC = 29;
    public static final int TYPE_DOCK = 13;
    public static final int TYPE_FM = 14;
    public static final int TYPE_FM_TUNER = 16;
    public static final int TYPE_TV_TUNER = 17;
    public static final int TYPE_LINE_DIGITAL = 24;
    public static final int TYPE_HEARING_AID = 17;
    public static final int TYPE_REMOTE_SUBMIX = 25;
    public static final int TYPE_ECHO_REFERENCE = 26;
    public static final int TYPE_BUILTIN_SPEAKER_SAFE = 27;
    public static final int TYPE_HDMI_EARC_LEGACY = 28;

    private final int mId;
    private final int mType;
    private final String mName;
    private final String mProduct;
    private final String mAddress;
    private final boolean mSource;
    private final int[] mRates;
    private final int[] mChannels;

    public AudioDeviceInfo(int id, int type, String name, String product, String address,
                           boolean source, int[] rates, int[] channels) {
        mId = id; mType = type; mName = name; mProduct = product; mAddress = address;
        mSource = source; mRates = rates; mChannels = channels;
    }

    public int getId() { return mId; }
    public int getType() { return mType; }
    public CharSequence getProductName() { return mProduct == null ? mName : mProduct; }
    public String getAddress() { return mAddress == null ? "" : mAddress; }
    public boolean isSource() { return mSource; }
    public boolean isSink() { return !mSource; }
    public int[] getSampleRates() { return mRates; }
    public int[] getChannelCounts() { return mChannels; }
    public int[] getChannelMasks() { return new int[0]; }
    public int[] getEncodings() { return new int[0]; }
    public boolean equals(Object o) { return o instanceof AudioDeviceInfo && ((AudioDeviceInfo) o).mId == mId; }
    public int hashCode() { return mId; }
}
