import java.util.regex.Pattern;

/**
 * LibreHU fork: compiles the regular expressions of the app, and LibreHU Helper's, with the regex engine of the Android
 * it runs on (ICU, which differs from the JDK's and from one Android version to the next), and checks the MAC address
 * ones on a few strings. A pattern one engine refuses stops the app at the first use of its object: MacAddressPolicy's
 * set starting with « [: » on Android 9. Run on emulators from Android 4.1 (the app's minSdk 16) by
 * .github/workflows/librehu-regex-check.yml, through app_process (tools/librehu/regex_check.sh).
 *
 * Output: one line per pattern (OK / FAIL), « RESULT new_fail=N old_fail=M » at the end; « old » are the patterns
 * replaced by the fix, expected to fail somewhere, « new » must all pass.
 */
public class RegexCheck {
    static int newFail = 0;
    static int oldFail = 0;

    /** Compiles [regex]; with [yes] / [no], checks what it must find (find(), as Kotlin's Regex.find / containsMatchIn) or not. */
    static Pattern check(String kind, String name, String regex, String[] yes, String[] no) {
        Pattern p;
        try {
            p = Pattern.compile(regex);
        } catch (Throwable e) {
            System.out.println("FAIL " + kind + " " + name + ": " + e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()).replace('\n', ' '));
            if (kind.equals("old")) oldFail++; else newFail++;
            return null;
        }
        for (String s : yes) {
            if (!p.matcher(s).find()) {
                System.out.println("FAIL " + kind + " " + name + ": no match in \"" + s + "\"");
                if (kind.equals("old")) oldFail++; else newFail++;
                return p;
            }
        }
        for (String s : no) {
            if (p.matcher(s).find()) {
                System.out.println("FAIL " + kind + " " + name + ": unexpected match in \"" + s + "\"");
                if (kind.equals("old")) oldFail++; else newFail++;
                return p;
            }
        }
        System.out.println("OK   " + kind + " " + name);
        return p;
    }

    static Pattern check(String kind, String name, String regex) {
        return check(kind, name, regex, new String[0], new String[0]);
    }

    public static void main(String[] args) throws Exception {
        Class<?> v = Class.forName("android.os.Build$VERSION");
        System.out.println("sdk " + v.getField("SDK_INT").getInt(null) + " release " + v.getField("RELEASE").get(null));
        String[] macYes = {"AA:BB:CC:DD:EE:FF", "AA-BB-CC-DD-EE-FF"};
        String[] macNo = {"AABBCCDDEEFF", "AA:BB:CC:DD:EE", "AA_BB_CC_DD_EE_FF"};

        // Open Headunit (app/src/main/java/com/andrerinas/openheadunit/…), in the order of the sources.
        check("old", "MacAddressPolicy.SEPARATED (before the fix)", "^[0-9A-F]{2}([:-][0-9A-F]{2}){5}$", macYes, macNo);
        check("new", "MacAddressPolicy.SEPARATED", "^[0-9A-F]{2}([\\-:][0-9A-F]{2}){5}$", macYes, macNo);
        check("new", "MacAddressPolicy.BARE", "^[0-9A-F]{12}$", new String[] {"AABBCCDDEEFF"}, new String[] {"AA:BB:CC:DD:EE:FF"});
        check("new", "AutomationOutputPolicy", "/+", new String[] {"a//b"}, new String[0]);
        check("new", "BluetoothPairingSession", "[0-9a-fA-F]{2}(:[0-9a-fA-F]{2}){5}", new String[] {"aa:bb:cc:dd:ee:ff"}, new String[] {"aa-bb"});
        check("new", "PerformanceOverlay / LabelFitPolicy", "\\s+", new String[] {"a  b"}, new String[] {"ab"});
        check("new", "AboutFragment h4", "### (.*)", new String[] {"### Title"}, new String[0]);
        check("new", "AboutFragment h3", "## (.*)", new String[] {"## Title"}, new String[0]);
        check("new", "AboutFragment h2", "# (.*)", new String[] {"# Title"}, new String[0]);
        check("new", "AboutFragment bold", "\\*\\*(.*?)\\*\\*", new String[] {"a **b** c"}, new String[0]);
        check("new", "AboutFragment list", "\n- (.*)", new String[] {"a\n- b"}, new String[0]);
        check("new", "WifiDirectManager / InterfaceMacReader", "link/ether (([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2})",
            new String[] {"link/ether 02:00:00:00:00:00 brd"}, new String[0]);
        check("new", "P2pGroupIdentityPolicy", "^DIRECT-[a-zA-Z0-9]{2}.*", new String[] {"DIRECT-ab-Phone"}, new String[] {"AndroidAP"});
        check("new", "ProjectionDeepLink dongle", "AAWireless-.*", new String[] {"AAWireless-1234"}, new String[0]);
        check("new", "ProjectionDeepLink BSSID", "^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$", new String[] {"aa:BB:cc:DD:ee:FF"}, new String[] {"aa-bb-cc-dd-ee-ff"});
        check("new", "ZbtIdentityProbe", "(?:[0-9a-fA-F]{12}|(?:[0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2})", new String[] {"aabbccddeeff"}, new String[0]);
        check("new", "BluetoothPeerInventory compact", "[0-9a-fA-F]{12}", new String[] {"aabbccddeeff"}, new String[0]);
        check("new", "BluetoothPeerInventory colon / LibreHuVehicleSync", "([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}", new String[] {"aa:bb:cc:dd:ee:ff"}, new String[0]);
        check("new", "SettingsBackupManager", "(?:open-headunit|headunit-revived)-settings-\\d{8}-\\d{6}\\.json",
            new String[] {"open-headunit-settings-20261108-120000.json"}, new String[0]);
        check("new", "AdbManager prompt", ".*:[/#$] *", new String[] {"generic_x86:/ $ "}, new String[0]);
        check("new", "RootRequestReceiver", "[A-Za-z0-9_.]{1,100}", new String[] {"org.librehu.service"}, new String[0]);

        // LibreHU Helper (report/Redactor.kt, device/VendorApps.kt…): its minSdk is 28, checked here up to the newest.
        check("old", "Helper Redactor.MAC (before the fix)", "(?<![0-9A-Fa-f:])(?:[0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}(?![0-9A-Fa-f:])",
            new String[] {"bt 00:1a:7d:da:71:13 x", "aa-bb-cc-dd-ee-ff"}, new String[] {"1:2:3"});
        check("new", "Helper Redactor.MAC", "(?<![0-9A-Fa-f:])(?:[0-9A-Fa-f]{2}[\\-:]){5}[0-9A-Fa-f]{2}(?![0-9A-Fa-f:])",
            new String[] {"bt 00:1a:7d:da:71:13 x", "aa-bb-cc-dd-ee-ff"}, new String[] {"1:2:3"});
        check("new", "Helper Redactor.NMEA", "(\\$G[PNLAB](?:GGA|RMC|GLL|GNS)),\\S*", new String[] {"$GPRMC,123519,A"}, new String[0]);
        check("new", "Helper Redactor.LOCATION", "Location\\[(\\w+) -?\\d+\\.\\d+,-?\\d+\\.\\d+", new String[] {"Location[gps 1.5,2.5 acc=3]"}, new String[0]);
        check("old", "Helper Redactor.LAT_LON (before the fix)", "(?i)\\b(lat|latitude|lon|lng|longitude)(\\s*[=:]\\s*)-?\\d+\\.\\d+", new String[] {"lat=1.5"}, new String[0]);
        check("new", "Helper Redactor.LAT_LON", "(?i)\\b(lat|latitude|lon|lng|longitude)(\\s*(?:=|:)\\s*)-?\\d+\\.\\d+", new String[] {"lat=1.5", "lon: -2.25"}, new String[0]);
        check("new", "Helper Redactor.SECRET_PROPS",
            "(?i)(serial|serialno|imei|meid|iccid|imsi|wifimac|wlan.?mac|btmac|bt.?addr|bdaddr|macaddr|\\.sn$|_sn$|uuid)",
            new String[] {"ro.serialno"}, new String[] {"ro.build.id"});
        check("new", "Helper Redactor.PROP", "^\\[(.+?)]: \\[(.*)]$", new String[] {"[ro.a]: [b]"}, new String[0]);
        check("new", "Helper VendorApps.KEYWORDS",
            "(?i)(canbus|\\bcan\\b|\\.can|mcu|ivi|carservice|car\\.service|radio|fmradio|\\bbt|bluetooth|"
                + "avin|aux|dvr|backcar|reverse|cam|steer|wheel|swc|launcher|settings|ms\\b|\\.ms$|services?$)",
            new String[] {"com.jancar.radio"}, new String[0]);
        check("new", "Helper Su / HardwareScan tty", "^/dev/tty(S|MT|HS|HSL|USB|ACM|AMA|SAC|GS)\\d+$", new String[] {"/dev/ttyS3"}, new String[0]);
        check("new", "Helper Dumps boot images", "^(boot|recovery|dtbo|vbmeta|vendor_boot|init_boot|dtb|lk|logo)(_a|_b)?$", new String[] {"boot_a"}, new String[0]);
        check("new", "Helper Output.stamp", "[^A-Za-z0-9._-]+", new String[] {"a b"}, new String[0]);
        check("new", "Helper Scans", "(\\S+) -> (\\S+)$", new String[] {"a -> b"}, new String[0]);

        System.out.println("RESULT new_fail=" + newFail + " old_fail=" + oldFail);
    }
}
