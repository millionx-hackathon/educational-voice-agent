package dev.zisan.ultravox_twilio.config;

import com.twilio.Twilio;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Twilio SDK configuration.
 * Initializes Twilio client with account credentials on application startup.
 */
@Slf4j
@Configuration
public class TwilioConfig {

    @Value("${twilio.account-sid}")
    private String accountSid;

    @Value("${twilio.auth-token}")
    private String authToken;

    @PostConstruct
    public void init() {
        log.info("Initializing Twilio with Account SID: {}...",
                accountSid.substring(0, Math.min(10, accountSid.length())));
        Twilio.init(accountSid, authToken);
        log.info("Twilio SDK initialized successfully");
    }
}
