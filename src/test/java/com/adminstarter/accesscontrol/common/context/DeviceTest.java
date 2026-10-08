package com.adminstarter.accesscontrol.common.context;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DeviceTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Safari/537.36 Edg/140.0 | Edge    | Windows
            Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Safari/537.36          | Chrome  | Windows
            Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15  | Safari  | macOS
            Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile Safari/604.1 | Safari  | iOS
            Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36           | Chrome  | Android
            Mozilla/5.0 (X11; Linux x86_64; rv:130.0) Gecko/20100101 Firefox/130.0                                             | Firefox | Linux
            curl/8.9.1                                                                                                          | Browser | Unknown
            """)
    void names_the_browser_and_os_like_the_dashboard_does(String userAgent, String browser, String os) {
        assertThat(Device.of(userAgent)).isEqualTo(new Device(browser, os));
    }

    @Test
    void no_user_agent_is_an_unknown_browser() {
        assertThat(Device.of(null).label()).isEqualTo("Browser · Unknown");
    }
}
