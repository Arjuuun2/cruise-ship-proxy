package com.example.offshore_proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class HTTPClient {
    private static final Logger logger = LoggerFactory.getLogger(HTTPClient.class);
    private static final Pattern REQUEST_LINE_PATTERN = Pattern.compile("(GET|POST|PUT|DELETE|HEAD|OPTIONS) (http://[^\\s]+|/[^\\s]*) HTTP/\\d\\.\\d");
    private static final Pattern HOST_PATTERN = Pattern.compile("Host: ([^\\r\\n]+)");

    public byte[] executeRequest(byte[] requestData) throws IOException {
        String requestStr = new String(requestData);
        String method = extractMethod(requestStr);
        URL url = extractURL(requestStr);
        if (url == null) {
            throw new IOException("Failed to parse URL from request");
        }
        logger.info("Executing {} request to {}", method, url);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        Map<String, String> headers = extractHeaders(requestStr);
        for (Map.Entry<String, String> header : headers.entrySet()) {
            if (!header.getKey().equalsIgnoreCase("host") && !header.getKey().equalsIgnoreCase("connection") && !header.getKey().equalsIgnoreCase("proxy-connection")) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
        }
        connection.setDoInput(true);
        if (method.equals("POST") || method.equals("PUT")) {
            connection.setDoOutput(true);
            byte[] body = extractBody(requestData);
            if (body.length > 0) {
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(body);
                    os.flush();
                }
            }
        }
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        try {
            int statusCode = connection.getResponseCode();
            String statusLine = "HTTP/1.1 " + statusCode + " " + connection.getResponseMessage() + "\r\n";
            response.write(statusLine.getBytes());
            for (Map.Entry<String, java.util.List<String>> header : connection.getHeaderFields().entrySet()) {
                if (header.getKey() != null) {
                    for (String value : header.getValue()) {
                        String headerLine = header.getKey() + ": " + value + "\r\n";
                        response.write(headerLine.getBytes());
                    }
                }
            }
            response.write("\r\n".getBytes());
            try (InputStream is = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream()) {
                if (is != null) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        response.write(buffer, 0, bytesRead);
                    }
                }
            }
            logger.info("Received response with status code: {}", statusCode);
        } catch (IOException e) {
            logger.error("Error executing HTTP request", e);
            String errorMessage = "Error: " + e.getMessage();
            String errorResponse = "HTTP/1.1 502 Bad Gateway\r\n" + "Content-Type: text/plain\r\n" + "Content-Length: " + errorMessage.length() + "\r\n" + "\r\n" + errorMessage;
            return errorResponse.getBytes();
        } finally {
            connection.disconnect();
        }
        return response.toByteArray();
    }

    private String extractMethod(String request) {
        Matcher matcher = REQUEST_LINE_PATTERN.matcher(request);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "GET";
    }

    private URL extractURL(String request) throws MalformedURLException {
        Matcher requestMatcher = REQUEST_LINE_PATTERN.matcher(request);
        if (requestMatcher.find()) {
            String urlStr = requestMatcher.group(2);
            if (urlStr.startsWith("http://") || urlStr.startsWith("https://")) {
                return new URL(urlStr);
            } else {
                Matcher hostMatcher = HOST_PATTERN.matcher(request);
                if (hostMatcher.find()) {
                    String host = hostMatcher.group(1);
                    String schema = request.toLowerCase().contains("https://") ? "https" : "http";
                    return new URL(schema + "://" + host + urlStr);
                }
            }
        }
        return null;
    }

    private Map<String, String> extractHeaders(String request) {
        Map<String, String> headers = new HashMap<>();
        String[] lines = request.split("\r\n");
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) {
                break;
            }
            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                String name = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();
                headers.put(name, value);
            }
        }
        return headers;
    }

    private byte[] extractBody(byte[] requestData) {
        String requestStr = new String(requestData);
        int bodyStart = requestStr.indexOf("\r\n\r\n");
        if (bodyStart > 0 && bodyStart + 4 < requestData.length) {
            int bodyLength = requestData.length - (bodyStart + 4);
            byte[] body = new byte[bodyLength];
            System.arraycopy(requestData, bodyStart + 4, body, 0, bodyLength);
            return body;
        }
        return new byte[0];
    }
}
