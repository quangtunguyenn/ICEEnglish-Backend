package com.ice.iceenglish.core.time;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ServerTimeConfiguration {

    @Bean
    public Clock serverClock() {
        return ServerTime.CLOCK;
    }
}
