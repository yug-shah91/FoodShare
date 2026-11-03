package com.foodshare.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Lets ExpiryJob run every minute.
// It lives in its own class (not on FoodShareApplication) so that database tests (@DataJpaTest) do not
// start the real scheduler in the background while they check ExpiryJob with a fixed "now".
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
