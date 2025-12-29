package dev.zisan.ultravox_twilio.controller;

import com.twilio.twiml.VoiceResponse;
import com.twilio.twiml.voice.Connect;
import com.twilio.twiml.voice.Say;
import com.twilio.twiml.voice.Stream;
import dev.zisan.ultravox_twilio.service.UltravoxService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

/**
 * Twilio webhook controller for handling incoming voice calls.
 * Creates Ultravox sessions and connects them via TwiML Stream.
 */
@Slf4j
@RestController
@RequestMapping("/api/twilio")
@RequiredArgsConstructor
public class TwilioWebhookController {

    private final UltravoxService ultravoxService;

    /**
     * Handle incoming calls from Twilio.
     * Creates an Ultravox session and returns TwiML to connect the call.
     */
    @PostMapping(value = "/incoming-call", produces = MediaType.APPLICATION_XML_VALUE)
    public String handleIncomingCall(
            @RequestParam(value = "CallSid", required = false) String callSid,
            @RequestParam(value = "From", required = false) String from,
            HttpServletRequest request) {

        log.info("Incoming call - CallSid: {}, From: {}", callSid, from);

        try {
            // Determine the base URL for callbacks
            String baseUrl = getBaseUrl(request);
            log.info("Using base URL for callbacks: {}", baseUrl);

            // Create Ultravox session
            String joinUrl = ultravoxService.createCall(callSid, baseUrl);
            log.info("Ultravox session created, connecting stream to: {}", joinUrl);

            // Build TwiML response with Stream to connect to Ultravox
            Stream stream = new Stream.Builder()
                    .url(joinUrl)
                    .build();

            Connect connect = new Connect.Builder()
                    .stream(stream)
                    .build();

            VoiceResponse response = new VoiceResponse.Builder()
                    .connect(connect)
                    .build();

            String twiml = response.toXml();
            log.debug("Generated TwiML: {}", twiml);
            return twiml;

        } catch (Exception e) {
            log.error("Error handling incoming call", e);
            return buildErrorResponse();
        }
    }

    /**
     * Handle call status updates from Twilio.
     */
    @PostMapping("/call-status")
    public String handleCallStatus(
            @RequestParam(value = "CallSid", required = false) String callSid,
            @RequestParam(value = "CallStatus", required = false) String callStatus) {

        log.info("Call status update - CallSid: {}, Status: {}", callSid, callStatus);
        return "OK";
    }

    /**
     * Get the base URL for this server (for ngrok tunnels in development).
     */
    private String getBaseUrl(HttpServletRequest request) {
        // Check for X-Forwarded headers (ngrok, reverse proxy)
        String forwardedHost = request.getHeader("X-Forwarded-Host");
        String forwardedProto = request.getHeader("X-Forwarded-Proto");

        if (forwardedHost != null && forwardedProto != null) {
            return forwardedProto + "://" + forwardedHost;
        }

        // Fall back to request URL
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();

        if ((scheme.equals("http") && serverPort == 80) ||
                (scheme.equals("https") && serverPort == 443)) {
            return scheme + "://" + serverName;
        }

        return scheme + "://" + serverName + ":" + serverPort;
    }

    /**
     * Build error response TwiML.
     */
    private String buildErrorResponse() {
        try {
            VoiceResponse response = new VoiceResponse.Builder()
                    .say(new Say.Builder(
                            "We're sorry, but we're experiencing technical difficulties. " +
                                    "Please try again later.")
                            .build())
                    .build();
            return response.toXml();
        } catch (Exception e) {
            log.error("Failed to build error response", e);
            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                    "<Response><Say>An error occurred.</Say></Response>";
        }
    }
}
