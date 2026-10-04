package android.hardware.usb;

public class UsbDevice {
    private final int mVid, mPid;
    private final String mName, mProduct, mManufacturer, mSerial, mVersion;
    private final UsbInterface[] mInterfaces;

    public UsbDevice(int vid, int pid, String name, String product, String manufacturer,
                     String serial, String version, UsbInterface[] interfaces) {
        mVid = vid; mPid = pid; mName = name; mProduct = product;
        mManufacturer = manufacturer; mSerial = serial; mVersion = version;
        mInterfaces = interfaces;
    }

    public int getVendorId() { return mVid; }
    public int getProductId() { return mPid; }
    public String getDeviceName() { return mName; }
    public String getProductName() { return mProduct; }
    public String getManufacturerName() { return mManufacturer; }
    public String getSerialNumber() { return mSerial; }
    public String getVersion() { return mVersion; }
    public int getInterfaceCount() { return mInterfaces.length; }
    public UsbInterface getInterface(int i) { return mInterfaces[i]; }
    public String toString() { return mName; }
}
