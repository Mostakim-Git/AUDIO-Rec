package android.hardware.usb;

public class UsbInterface {
    private final int mId, mClass, mSubclass, mProtocol, mEndpoints;

    public UsbInterface(int id, int cls, int sub, int proto, int endpoints) {
        mId = id; mClass = cls; mSubclass = sub; mProtocol = proto; mEndpoints = endpoints;
    }

    public int getId() { return mId; }
    public int getInterfaceClass() { return mClass; }
    public int getInterfaceSubclass() { return mSubclass; }
    public int getInterfaceProtocol() { return mProtocol; }
    public int getEndpointCount() { return mEndpoints; }
    public UsbEndpoint getEndpoint(int i) { return new UsbEndpoint(); }
}
