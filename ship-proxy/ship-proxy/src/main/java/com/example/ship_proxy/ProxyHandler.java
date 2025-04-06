package com.example.ship_proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ProxyHandler {
   private static final Logger logger = LoggerFactory.getLogger(ProxyHandler.class);

   private int proxyPort;

   private String offshoreProxyHost;

   private int offshoreProxyPort;

   private final AtomicLong requestIdGenerator = new AtomicLong(1);
   private final ConcurrentHashMap<Long, Clientconnection> pendingRequests = new ConcurrentHashMap<Long, Clientconnection>();
   private final BlockingQueue<ProxyRequest> requestQueue = new LinkedBlockingQueue<>();

   private com.example.ship_proxy.TCPClient tcpClient;
   private boolean running = true;


    public void start() {
        //start the client connection to offshore proxy
        tcpClient = new com.example.ship_proxy.TCPClient(offshoreProxyHost, offshoreProxyPort, this);
        tcpClient.connect();
        new Thread(this::processRequestsFromQueue).start();
        new Thread(this::handleResponses).start();
        try (ServerSocket serverSocket = new ServerSocket(proxyPort)) {
            logger.info("Ship proxy listening on port {}", proxyPort);
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    handleClientRequest(clientSocket);
                } catch (Exception e) {
                    logger.error("Error accepting client connection", e);
                }
            }
        } catch (IOException e) {
            logger.info("Failed to start ship proxy");
        }
    }

    private void handleClientRequest(Socket clientSocket) {
        new Thread(() -> {
            try {
                long requestId = requestIdGenerator.getAndIncrement();
                logger.info("Handling client request with ID: {}", requestId);
                InputStream clientIn = clientSocket.getInputStream();
                ByteArrayOutputStream requestBytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = clientIn.read(buffer)) != -1) {
                    requestBytes.write(buffer, 0, bytesRead);
                    String requestData = requestBytes.toString();
                    if (requestData.contains("\r\n\r\n")) {
                        if (requestData.startsWith("POST") || requestData.startsWith("PUT")) {
                            int contentLengthIndex = requestData.indexOf("Content-Length:");
                            if (contentLengthIndex > 0) {
                                int endOfLine = requestData.indexOf("\r\n", contentLengthIndex);
                                String contentLengthValue = requestData.substring(contentLengthIndex + 15, endOfLine).trim();
                                int contentLength = Integer.parseInt(contentLengthValue);
                                int headerEndIndex = requestData.indexOf("\r\n\r\n") + 4;
                                int bytesAlreadyRead = requestBytes.size() - headerEndIndex;
                                int bytesStillNeeded = contentLength - bytesAlreadyRead;
                                while (bytesStillNeeded > 0 && (bytesRead = clientIn.read(buffer)) != -1) {
                                    requestBytes.write(buffer, 0, bytesRead);
                                    bytesStillNeeded -= bytesRead;
                                }
                            }
                        }
                        break;
                    }
                }
                //store the client connection for later response
                Clientconnection clientconnection = new Clientconnection(clientSocket, requestId);
                pendingRequests.put(requestId, clientconnection);

                //Queue the request for processing
                ProxyRequest proxyRequest = new ProxyRequest(requestId, requestBytes.toByteArray());
                requestQueue.put(proxyRequest);
            } catch (Exception e) {
                logger.error("Error handling client request", e);
                try {
                    clientSocket.close();
                } catch (IOException ex) {
                    logger.error("Error closing client socket", ex);
                }
            }
        }).start();
    }

    private void processRequestsFromQueue() {
        while (running) {
            try {
                ProxyRequest request = requestQueue.take();
                logger.info("Processing request ID: {}", request.getRequestId());

                //send request to offshore proxy
                tcpClient.sendRequest(request);
            } catch (InterruptedException e) {
                logger.error("Request processing interrupted", e);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.error("Error processing request", e);
            }
        }
    }

    private void handleResponses() {
        while (running) {
            try {
                ProxyResponse response = tcpClient.getNextResponse();
                if (response != null) {
                    Clientconnection clientConnection = pendingRequests.remove(response.getRequestId());
                    if (clientConnection != null) {
                        logger.info("Sending response for request ID: {}", response.getRequestId());

                        //send response back to client
                        OutputStream clientOut = clientConnection.getClientSocket().getOutputStream();
                        clientOut.write(response.getResponseData());
                        clientOut.flush();
                        clientConnection.getClientSocket().close();
                    } else {
                        logger.warn("No pending request found for response ID: {}", response.getRequestId());
                    }
                }
            } catch (Exception e) {
                logger.error("Error handling response", e);
            }
        }
    }

    static class Clientconnection {
        private final Socket clientSocket;
        private final long requestId;

        public Clientconnection(Socket clientSocket, long requestId) {
            this.clientSocket = clientSocket;
            this.requestId = requestId;
        }

        public Socket getClientSocket() {
            return clientSocket;
        }

        public long getRequestId() {
            return requestId;
        }
    }

    static class ProxyRequest {
        private final long requestId;
        private final byte[] requestData;

        public ProxyRequest(long requestId, byte[] requestData) {
            this.requestId = requestId;
            this.requestData = requestData;
        }

        public long getRequestId() {
            return requestId;
        }

        public byte[] getRequestData() {
            return requestData;
        }
    }

    static class ProxyResponse {
        private final long requestId;
        private final byte[] responseData;

        public ProxyResponse(long requestId, byte[] responseData) {
            this.requestId = requestId;
            this.responseData = responseData;
        }

        public long getRequestId() {
            return requestId;
        }

        public byte[] getResponseData() {
            return responseData;
        }
    }
}
