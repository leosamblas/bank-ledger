package com.bankledger.notification.application.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class EmailTemplateLoader {

    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    public String loadTemplate(String path) {
        if (path == null) {
            return null;
        }
        return cache.computeIfAbsent(path, this::readClasspathResource);
    }

    private String readClasspathResource(String path) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                log.warn("Email template not found on classpath: {}. Falling back to plain text.", path);
                return null;
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Failed to load email template: {}. Falling back to plain text.", path, e);
            return null;
        }
    }
}
