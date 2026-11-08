package com.ice.iceenglish;

import com.ice.iceenglish.core.time.ServerTime;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class IceenglishApplication {

	static {
		TimeZone.setDefault(TimeZone.getTimeZone(ServerTime.ZONE_ID));
	}

	public static void main(String[] args) {
		SpringApplication.run(IceenglishApplication.class, args);
	}

}
