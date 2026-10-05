package android.hardware.usb;

import java.util.HashMap;
import java.util.Map;

public class UsbManager {
    public static final String ACTION_USB_PERMISSION = "android.hardware.usb.action.USB_PERMISSION";

    private final Map<String, UsbDevice> mDevices = new HashMap<>();

    public Map<String, UsbDevice> getDeviceList() { return mDevices; }
    public void requestPermission(UsbDevice device, android.app.PendingIntent pi) { }
    public boolean hasPermission(UsbDevice device) { return true; }
    public UsbInterface getInterface(UsbDevice device, int index) { return device.getInterface(index); }
}
