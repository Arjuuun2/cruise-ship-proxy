package com.example.ship_proxy;

import com.example.ship_proxy.ProxyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class TCPClient {
    private static final Logger logger = LoggerFactory.getLogger(TCPClient.class);
    private final String offshoreHost;
    private final int offshorePort;
    private final ProxyHandler proxyHandler;
    private Socket connection;
    private DataOutputStream outputStream;
    private DataInputStream inputStream;
    private final BlockingQueue<ProxyHandler.ProxyResponse> responseQueue = new LinkedBlockingQueue<>();
    private boolean connected = false;

    public TCPClient(String offshoreHost, int offshorePort, ProxyHandler proxyHandler) {
        this.offshoreHost = offshoreHost;
        this.offshorePort = offshorePort;
        this.proxyHandler = proxyHandler;

        logger.info("TCPClient created with offshoreHost={}, offshorePort={}",
                this.offshoreHost, this.offshorePort);
    }

    public void connect() {
        new Thread(() -> {
            while (!connected) {
                try {
                    logger.info("Connecting to offshore proxy at {}:{}", offshoreHost, offshorePort);
                    connection = new Socket(offshoreHost, offshorePort);
                    outputStream = new DataOutputStream(connection.getOutputStream());
                    inputStream = new DataInputStream(connection.getInputStream());
                    connected = true;
                    logger.info("Connected to offshore proxy");
                    startResponseReceiver();
                } catch (IOException e) {
                    logger.error("Failed to connect to offshore proxy. Retrying in 5 seconds...", e);
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }).start();
    }

    public void sendRequest(ProxyHandler.ProxyRequest request) {
        if (!connected) {
            logger.error("Not connected to offshore proxy");
            return;
        }
        try {
            ByteBuffer header = ByteBuffer.allocate(12);
            header.putLong(request.getRequestId());
            header.putInt(request.getRequestData().length);
            synchronized (this) {
                outputStream.write(header.array());
                outputStream.write(request.getRequestData());
                outputStream.flush();
            }
            logger.info("Sent request ID: {} to offshore proxy", request.getRequestId());
        } catch (IOException e) {
            logger.error("Error sending request to offshore proxy", e);
            handleDisconnection();
        }
    }

    private void startResponseReceiver() {
        new Thread(() -> {
            while (connected) {
                try {
                    long requestId = inputStream.readLong();
                    int length = inputStream.readInt();
                    logger.info("Receiving response for request ID: {}, length: {}", requestId, length);
                    byte[] responseData = new byte[length];
                    int bytesRead = 0;
                    while (bytesRead < length) {
                        int read = inputStream.read(responseData, bytesRead, length - bytesRead);
                        if (read == -1) {
                            throw new EOFException("End of stream reached");
                        }
                        bytesRead += read;
                    }
                    ProxyHandler.ProxyResponse response = new ProxyHandler.ProxyResponse(requestId, responseData);
                    responseQueue.put(response);
                } catch (EOFException | InterruptedException e) {
                    logger.error("Connection to offshore proxy lost", e);
                    handleDisconnection();
                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    break;
                } catch (IOException e) {
                    logger.error("Error receiving response from offshore proxy", e);
                    handleDisconnection();
                    break;
                }
            }
        }).start();
    }

    private void handleDisconnection() {
        connected = false;
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (IOException e) {
            logger.error("Error closing connection", e);
        }
        connect();
    }

    public ProxyHandler.ProxyResponse getNextResponse() throws InterruptedException {
        return responseQueue.take();
    }
}