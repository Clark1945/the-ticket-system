package com.ticketsystem.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class OtpService {

    private final StringRedisTemplate redisTemplate;

    @Value("${app.otp.ttl}")
    private long otpTtl;

    @Value("${app.otp.length}")
    private int otpLength;

    public String generateAndSave(Long merchantId, String type) {
        String otp = generateOtp();
        redisTemplate.opsForValue().set(buildKey(merchantId, type), otp, otpTtl, TimeUnit.SECONDS);
        return otp;
    }

    public boolean validate(Long merchantId, String type, String otp) {
        String stored = redisTemplate.opsForValue().get(buildKey(merchantId, type));
        return stored != null && stored.equals(otp);
    }

    public void delete(Long merchantId, String type) {
        redisTemplate.delete(buildKey(merchantId, type));
    }

    public boolean exists(Long merchantId, String type) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(buildKey(merchantId, type)));
    }

    private String buildKey(Long merchantId, String type) {
        return "otp:merchant:" + merchantId + ":" + type;
    }

    private String generateOtp() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(otpLength);
        for (int i = 0; i < otpLength; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
