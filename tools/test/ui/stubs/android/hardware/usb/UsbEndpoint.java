package android.hardware.usb;

public class UsbEndpoint {
    public static final int TYPE_ISOCHRONOUS = 1;
    public static final int TYPE_BULK = 2;
    public static final int TYPE_INTERRUPT = 3;
    public static final int DIRECTION_IN = 0x80;
    public static final int DIRECTION_OUT = 0x00;

    public int getType() { return TYPE_ISOCHRONOUS; }
    public int getDirection() { return DIRECTION_IN; }
    public int getMaxPacketSize() { return 512; }
    public int getInterval() { return 1; }
}
