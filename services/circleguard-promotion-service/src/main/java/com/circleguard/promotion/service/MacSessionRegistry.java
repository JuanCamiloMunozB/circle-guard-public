package com.circleguard.promotion.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class MacSessionRegistry {
    private final StringRedisTemplate redisTemplate;
    private static final String KEY_PREFIX = "session:mac:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(8);

    /** A MAC address: six colon- or hyphen-separated hex octets. */
    private static final Pattern MAC_PATTERN =
            Pattern.compile("^[0-9A-Fa-f]{2}([:-][0-9A-Fa-f]{2}){5}$");
    /** Anonymous identifiers are opaque tokens: letters, digits, hyphen, underscore only. */
    private static final Pattern ANONYMOUS_ID_PATTERN =
            Pattern.compile("^[A-Za-z0-9_-]{1,128}$");

    public void registerSession(String macAddress, String anonymousId) {
        String key = keyFor(macAddress);
        String value = validateAnonymousId(anonymousId);
        redisTemplate.opsForValue().set(key, value, DEFAULT_TTL);
    }

    public String getAnonymousId(String macAddress) {
        return redisTemplate.opsForValue().get(keyFor(macAddress));
    }

    public void closeSession(String macAddress) {
        redisTemplate.delete(keyFor(macAddress));
    }

    /**
     * Validates the MAC address against a strict allow-list and returns the
     * normalized Redis key. Rejecting any value outside the allow-list prevents
     * user-controlled data from being used to forge arbitrary Redis keys.
     */
    private static String keyFor(String macAddress) {
        if (macAddress == null || !MAC_PATTERN.matcher(macAddress).matches()) {
            throw new IllegalArgumentException("Invalid MAC address");
        }
        return KEY_PREFIX + macAddress.toLowerCase();
    }

    /**
     * Validates the anonymous identifier against a strict allow-list so that no
     * control characters or injection payloads can be persisted as the value.
     */
    private static String validateAnonymousId(String anonymousId) {
        if (anonymousId == null || !ANONYMOUS_ID_PATTERN.matcher(anonymousId).matches()) {
            throw new IllegalArgumentException("Invalid anonymous id");
        }
        return anonymousId;
    }
}
