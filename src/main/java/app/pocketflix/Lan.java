package app.pocketflix;

import java.net.*;
import java.util.*;

final class Lan {

  static boolean priv(InetAddress a) {
    return a instanceof Inet4Address && a.isSiteLocalAddress();
  }

  static List<InterfaceAddress> links() {
    List<InterfaceAddress> l = new ArrayList<>();
    try {
      for (NetworkInterface n : Collections.list(
        NetworkInterface.getNetworkInterfaces()
      ))
        if (
          n.isUp() &&
          !n.isLoopback() &&
          n
            .getName()
            .matches(
              "(wlan|wifi|ap|swlan|softap|eth|en|rndis|usb|br)[a-zA-Z0-9_.-]*"
            )
        ) for (InterfaceAddress a : n.getInterfaceAddresses())
          if (priv(a.getAddress())) l.add(a);
    } catch (Exception ignored) {}
    return l;
  }

  static String ip() {
    List<InterfaceAddress> l = links();
    return l.isEmpty() ? null : l.get(0).getAddress().getHostAddress();
  }

  static boolean allow(Socket s) {
    if (!priv(s.getInetAddress())) return false;
    for (InterfaceAddress i : links())
      if (i.getAddress().equals(s.getLocalAddress())) {
        byte[] a = i.getAddress().getAddress(),
          b = s.getInetAddress().getAddress();
        int bits = i.getNetworkPrefixLength();
        if (bits < 1) continue;
        boolean same = true;
        for (int k = 0; k < bits; k++) if (
          ((a[k / 8] ^ b[k / 8]) & (128 >> (k % 8))) != 0
        ) same = false;
        if (same) return true;
      }
    return false;
  }
}
