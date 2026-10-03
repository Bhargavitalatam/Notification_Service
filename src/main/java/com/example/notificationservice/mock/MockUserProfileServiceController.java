package com.example.notificationservice.mock;

import com.example.notificationservice.dto.UserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/mock-users")
public class MockUserProfileServiceController {

    private static final Logger log = LoggerFactory.getLogger(MockUserProfileServiceController.class);

    private final AtomicBoolean globalFail = new AtomicBoolean(false);
    private final AtomicLong globalDelayMs = new AtomicLong(0);

    @GetMapping("/{userId}")
    public ResponseEntity<UserProfile> getUserProfile(
            @PathVariable String userId,
            @RequestParam(value = "fail", required = false) Boolean fail,
            @RequestParam(value = "delay", required = false) Long delayMs) throws InterruptedException {

        long effectiveDelay = delayMs != null ? delayMs : globalDelayMs.get();
        boolean effectiveFail = fail != null ? fail : globalFail.get();

        if (effectiveDelay > 0) {
            log.info("MockUserProfileService: Simulating delay of {} ms for user {}", effectiveDelay, userId);
            Thread.sleep(effectiveDelay);
        }

        if (effectiveFail) {
            log.warn("MockUserProfileService: Simulating HTTP 500 failure for user {}", userId);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        String phonePrefix = (userId != null && userId.length() >= 3) ? userId.substring(0, 3) : "123";
        UserProfile profile = new UserProfile(userId, userId + "@example.com", "+1-" + phonePrefix + "-555-0100");
        return ResponseEntity.ok(profile);
    }

    @PostMapping("/configure")
    public ResponseEntity<Map<String, Object>> configureMock(
            @RequestParam(value = "fail", required = false) Boolean fail,
            @RequestParam(value = "delay", required = false) Long delayMs) {
        if (fail != null) {
            globalFail.set(fail);
        }
        if (delayMs != null) {
            globalDelayMs.set(delayMs);
        }
        log.info("MockUserProfileService configured: globalFail={}, globalDelayMs={}", globalFail.get(), globalDelayMs.get());
        return ResponseEntity.ok(Map.of(
                "globalFail", globalFail.get(),
                "globalDelayMs", globalDelayMs.get(),
                "status", "Configured successfully"
        ));
    }

    @GetMapping("/configure")
    public ResponseEntity<Map<String, Object>> getMockConfiguration() {
        return ResponseEntity.ok(Map.of(
                "globalFail", globalFail.get(),
                "globalDelayMs", globalDelayMs.get()
        ));
    }
}
