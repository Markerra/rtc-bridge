package me.markerra.rtcbridge.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class ResourceManager {
    public static String loadResource(String fileName) {
        String resourcePath = "/" + fileName;

        try (InputStream in = ResourceManager.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new RuntimeException("Resource not found in classpath: " + resourcePath);
            }

            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read resource: " + resourcePath, e);
        }
    }
}
