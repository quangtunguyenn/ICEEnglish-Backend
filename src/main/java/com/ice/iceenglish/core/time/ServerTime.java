package com.ice.iceenglish.core.time;

import java.time.Clock;
import java.time.ZoneId;

public final class ServerTime {

    public static final ZoneId ZONE_ID = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final Clock CLOCK = Clock.system(ZONE_ID);

    private ServerTime() {
    }
}
