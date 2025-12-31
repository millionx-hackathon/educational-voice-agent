package dev.zisan.ultravox_twilio.controller;

import com.twilio.twiml.VoiceResponse;
import com.twilio.twiml.voice.Connect;
import com.twilio.twiml.voice.Say;
import com.twilio.twiml.voice.Stream;
import dev.zisan.ultravox_twilio.service.ConversationSummaryService;
import dev.zisan.ultravox_twilio.service.UltravoxService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    private final ConversationSummaryService summaryService;

    // Store mapping of Twilio CallSid to Ultravox CallId and caller number
    private final Map<String, CallInfo> activeCallsMap = new ConcurrentHashMap<>();

    /**
     * Internal class to store call information.
     */
    private record CallInfo(String ultravoxCallId, String callerNumber) {
    }

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

            // Create Ultravox session and get both join URL and call ID
            UltravoxService.UltravoxCallResult callResult = ultravoxService.createCallWithId(callSid, baseUrl);
            log.info("Ultravox session created, Call ID: {}, connecting stream to: {}",
                    callResult.callId(), callResult.joinUrl());

            // Store the mapping for later summary generation
            activeCallsMap.put(callSid, new CallInfo(callResult.callId(), from));

            // Build TwiML response with Stream to connect to Ultravox
            // The action URL is called when the stream disconnects (call ends)
            Stream stream = new Stream.Builder()
                    .url(callResult.joinUrl())
                    .build();

            Connect connect = new Connect.Builder()
                    .stream(stream)
                    .action(baseUrl + "/api/twilio/stream-ended") // Called when stream ends
                    .build();

            VoiceResponse response = new VoiceResponse.Builder()
                    .connect(connect)
                    .build();

            String twiml = response.toXml();
            log.info("Generated TwiML with action URL: {}/api/twilio/stream-ended", baseUrl);
            log.debug("Generated TwiML: {}", twiml);
            return twiml;

        } catch (Exception e) {
            log.error("Error handling incoming call", e);
            return buildErrorResponse();
        }
    }

    /**
     * Debug endpoint to list active calls.
     * Returns mapping of Twilio CallSid to Ultravox CallId.
     * Use this to find the Ultravox call ID for manual summary generation.
     */
    @GetMapping("/active-calls")
    public Map<String, Map<String, String>> getActiveCalls() {
        log.info("Fetching active calls - count: {}", activeCallsMap.size());

        Map<String, Map<String, String>> result = new java.util.HashMap<>();
        activeCallsMap.forEach((twilioCallSid, callInfo) -> {
            result.put(twilioCallSid, Map.of(
                    "ultravoxCallId", callInfo.ultravoxCallId(),
                    "callerNumber", callInfo.callerNumber() != null ? callInfo.callerNumber() : "unknown"));
        });

        return result;
    }

    /**
     * Handle stream ended callback from Twilio.
     * This is called when the Ultravox stream disconnects (call ends).
     * Triggers automatic summary generation.
     */
    @PostMapping(value = "/stream-ended", produces = MediaType.APPLICATION_XML_VALUE)
    public String handleStreamEnded(
            @RequestParam(value = "CallSid", required = false) String callSid,
            @RequestParam(value = "CallStatus", required = false) String callStatus) {

        log.info("Stream ended - CallSid: {}, Status: {}", callSid, callStatus);

        // Generate summary when stream ends
        CallInfo callInfo = activeCallsMap.remove(callSid);

        if (callInfo != null) {
            log.info("Generating summary for ended call - CallSid: {}, UltravoxId: {}",
                    callSid, callInfo.ultravoxCallId());

            // Process asynchronously to not block the webhook response
            new Thread(() -> {
                try {
                    summaryService.processCompletedCall(callSid, callInfo.ultravoxCallId(),
                            callInfo.callerNumber());
                    log.info("Summary generated successfully for call: {}", callSid);
                } catch (Exception e) {
                    log.error("Error generating summary for call: {}", callSid, e);
                }
            }).start();
        } else {
            log.warn("No call info found for stream-ended call: {}", callSid);
        }

        // Return TwiML to hang up the call
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response><Hangup/></Response>";
    }

    /**
     * Handle call status updates from Twilio.
     * When call ends, trigger summary generation.
     */
    @PostMapping("/call-status")
    public String handleCallStatus(
            @RequestParam(value = "CallSid", required = false) String callSid,
            @RequestParam(value = "CallStatus", required = false) String callStatus,
            @RequestParam(value = "CallDuration", required = false) String callDuration) {

        log.info("Call status update - CallSid: {}, Status: {}, Duration: {}s", callSid, callStatus, callDuration);

        // When call is completed, generate summary
        if ("completed".equalsIgnoreCase(callStatus)) {
            CallInfo callInfo = activeCallsMap.remove(callSid);

            if (callInfo != null) {
                log.info("Call completed, generating summary for CallSid: {}, UltravoxId: {}",
                        callSid, callInfo.ultravoxCallId());

                // Process asynchronously to not block the webhook response
                new Thread(() -> {
                    try {
                        summaryService.processCompletedCall(callSid, callInfo.ultravoxCallId(),
                                callInfo.callerNumber());
                    } catch (Exception e) {
                        log.error("Error generating summary for call: {}", callSid, e);
                    }
                }).start();
            } else {
                log.warn("No call info found for completed call: {}", callSid);
            }
        }

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
