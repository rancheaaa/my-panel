package com.cq.agent.client.upload;

import com.google.common.util.concurrent.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("all")
public class UploadRateLimiter {

    private static final Logger logger = LoggerFactory.getLogger(UploadRateLimiter.class);

    private final RateLimiter rateLimiter;
    private final double maxRateKBPerSecond;

    public UploadRateLimiter(long bytesPerSecond) {
        this.rateLimiter = RateLimiter.create(bytesPerSecond / 1000.0);
        this.maxRateKBPerSecond = bytesPerSecond / 1024.0;
    }

    public void acquire(int bytes, String traceId) throws InterruptedException {
        if (bytes <= 0) {
            return;
        }
        
        long sleepMicros = (long) (rateLimiter.acquire(bytes) * 1_000_000);
        if (sleepMicros > 0) {
            logger.debug("[traceId={}] Rate limiting: slept {}μs to respect max rate of {} KB/s", 
                    traceId, sleepMicros, String.format("%.2f", maxRateKBPerSecond));
        }
    }

    public void shutdown() {
    }
}