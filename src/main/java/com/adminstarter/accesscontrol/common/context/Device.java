package com.adminstarter.accesscontrol.common.context;

import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** A browser and operating system, as the devices list and the activity log show them */
public record Device(String browser, String os) {

    private static final Pattern EDGE = Pattern.compile("Edg/");
    private static final Pattern FIREFOX = Pattern.compile("Firefox/");
    private static final Pattern CHROME = Pattern.compile("Chrome/");
    private static final Pattern SAFARI = Pattern.compile("Safari/");

    /**
     * Names from a User-Agent header, the same way admin-ng-starter's describeBrowser() does. Order matters: Edge also
     * says Chrome, Chrome also says Safari, Android also says Linux, iOS also says Mac OS X.
     */
    public static Device of(@Nullable String userAgent) {
        var agent = userAgent == null ? "" : userAgent;
        var browser = EDGE.matcher(agent).find() ? "Edge"
                : FIREFOX.matcher(agent).find() ? "Firefox"
                : CHROME.matcher(agent).find() ? "Chrome"
                : SAFARI.matcher(agent).find() ? "Safari"
                : "Browser";
        var os = agent.contains("Windows") ? "Windows"
                : agent.contains("iPhone") || agent.contains("iPad") ? "iOS"
                : agent.contains("Android") ? "Android"
                : agent.contains("Mac OS X") ? "macOS"
                : agent.contains("Linux") ? "Linux"
                : "Unknown";
        return new Device(browser, os);
    }

    /** "Chrome · Windows" */
    public String label() {
        return browser + " · " + os;
    }
}
