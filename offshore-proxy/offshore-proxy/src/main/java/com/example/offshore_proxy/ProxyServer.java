package com.example.offshore_proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class ProxyServer {
    private static final Logger logger = LoggerFactory.getLogger(ProxyServer.class);
    @Value("${offshore.proxy.port:9090}")
    private int proxyPort;
    @Autowired
    private HTTPClient httpClient;
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private boolean running = true;

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(proxyPort)) {
            logger.info("Offshore proxy server listening on port {}", proxyPort);
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    logger.info("Connection established with ship proxy from: {}", clientSocket.getInetAddress());
                    handleShipProxyConnection(clientSocket);
                } catch (Exception e) {
                    logger.error("Error accepting client connection", e);
                }
            }
        } catch (IOException e) {
            logger.error("Failed to start offshore proxy server", e);
        }
    }

    private void handleShipProxyConnection(Socket clientSocket) {
        executorService.submit(() -> {
            try {
                DataInputStream inputStream = new DataInputStream(clientSocket.getInputStream());
                DataOutputStream outputStream = new DataOutputStream(clientSocket.getOutputStream());
                while (running) {
                    try {
                        long requestId = inputStream.readLong();
                        int length = inputStream.readInt();
                        logger.info("Received request ID: {}, length: {}", requestId, length);
                        byte[] requestData = new byte[length];
                        int bytesRead = 0;
                        while (bytesRead < length) {
                            int read = inputStream.read(requestData, bytesRead, length - bytesRead);
                            if (read == -1) {
                                throw new EOFException("End of stream reached");
                            }
                            bytesRead += read;
                        }
                        processRequest(requestId, requestData, outputStream);
                    } catch (EOFException e) {
                        logger.error("Connection to ship proxy lost", e);
                        break;
                    }
                }
            } catch (IOException e) {
                logger.error("Error handling ship proxy connection", e);
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    logger.error("Error closing client socket", e);
                }
            }
        });
    }

    private void processRequest(long requestId, byte[] requestData, DataOutputStream outputStream) {
        executorService.submit(() -> {
            try {
                byte[] responseData = httpClient.executeRequest(requestData);
                ByteBuffer header = ByteBuffer.allocate(12);
                header.putLong(requestId);
                header.putInt(responseData.length);
                synchronized (outputStream) {
                    outputStream.write(header.array());
                    outputStream.write(responseData);
                    outputStream.flush();
                }
                logger.info("Sent response for request ID: {}, length: {}", requestId, responseData.length);
            } catch (Exception e) {
                logger.error("Error processing request ID: {}", requestId, e);
                String errorMessage = "Error: " + e.getMessage();
                byte[] errorData = generateErrorResponse(errorMessage);
                try {
                    ByteBuffer header = ByteBuffer.allocate(12);
                    header.putLong(requestId);
                    header.putInt(errorData.length);
                    synchronized (outputStream) {
                        outputStream.write(header.array());
                        outputStream.write(errorData);
                        outputStream.flush();
                    }
                } catch (IOException ioe) {
                    logger.error("Error sending error response", ioe);
                }
            }
        });
    }

    private byte[] generateErrorResponse(String errorMessage) {
        String response = "HTTP/1.1 500 Internal Server Error\r\n" + "Content-Type: text/plain\r\n" + "Content-Length: " + errorMessage.length() + "\r\n" + "\r\n" + errorMessage;
        return response.getBytes();
    }
}
