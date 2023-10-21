import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.io.Serializable;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.lang.RuntimeException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.lang.StringBuilder;

/**
 * Prototype for the communication reception and dispatch protocol for AutoNav Interpreter
 * Sending data to the interface is as follows:
 * <ul>
 * <li>Send the packet to the RoboRIO</li>
 * <li>The RoboRIO will then dispatch the packet to the driverstation</li>
 * <li>The driverstation will route the packet to the interface for interpretation</li>
 * <li>Sends back a 200 code if accepted</li>
 * <li>Sends back a 4xx or 5xx code if failed</li>
 * </ul>
 * Sending data to the server is as follows:
 * <ul>
 * <li>Send the packet directly to the server via ethernet</li>
 * <li>The server will send a 200 code if accepted</li>
 * <li>The server will send a 4xx or 5xx code if failed</li>
 * </ul>
 */
public class CommProto {
    public static void main(String[] args) {
        initServerConnection();
    }
    /**
     * Send data to the server or the interface
     * Interface protocol will route to RoboRIO via ethernet or other means
     * Server protocol will route directly to the server via ethernet
     * @param message The message object sent to the destination
     */
    public static void send(Message message) throws IOException {
        Targets target = message.target();
        DatagramSocket socket = new DatagramSocket();
        
        InetAddress address = InetAddress.getByName("192.168.0.0"); //TODO: Change this to the actual IP address
        int port = 8080; //TODO: Change this to the actual port
        
        byte[] data = convToBytes(message);
        DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
        
        socket.send(packet);
        socket.close();
    }
    
    /**
     * Convert a message to a byte array, usable when converting to a Datagram Packet
     * @param message Message to serialize
     */
    private static byte[] convToBytes(Message message) {
        ByteArrayOutputStream boas = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(boas)) {
            oos.writeObject(message);
            return boas.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Initalizes the server connection to recieve the incoming messages
     * This will be opened on port 8080, no server context
     */
    public static void initServerConnection() {
        try (ServerSocket serverSocket = new ServerSocket(8080)) {
            System.out.println("Server started on port 8080!");
            while (true) {
                try (Socket client = serverSocket.accept()) {
                    handleClient(client);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Handles the incoming requests from server and Interpreter
     * Packages into the Request object
     * Returns a response to the initial sender
     * @param client The socket of the server
     */
    private static void handleClient(Socket client) {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(client.getInputStream()));
        
            StringBuilder requestBuilder = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null && !line.isBlank()) {
                requestBuilder.append(line + "\r\n");
            }
        
            String request = requestBuilder.toString();
            String[] requestsLines = request.split("\r\n");
            String[] requestLine = requestsLines[0].split(" ");
            String method = requestLine[0];
            String target = requestLine[1];
            String httpVersion = requestLine[2];

            System.out.println("Received data: " + method + " " + target + " " + httpVersion);

            if ("POST".equals(method)) {
                int contentLength = 0;
                for (String header : requestsLines) {
                    if (header.startsWith("Content-Length: ")) {
                        contentLength = Integer.parseInt(header.substring(16));
                        break;
                    }
                }
                StringBuilder jsonData = new StringBuilder();
                for (int i = 0; i < contentLength; i++) {
                    jsonData.append((char) br.read());
                }
                System.out.println("JSON Data: " + jsonData.toString());
            }
            
        
            Request requestObject = new Request(method, target, httpVersion);
            //parseData(requestObject);
            
            /* TODO:
            * Ensure the received json is valid
            * If not send an error response back to the sender
            */
            Response response = new Response("RECEIVED", "The request was successfully received.");
            String json = "{\"name\":\"" + response.code() + "\",\"body\":" + response.body() + "\"}";
            OutputStream clientOutput = client.getOutputStream();
            clientOutput.write(("HTTP/1.1 200 OK\r\n").getBytes());
            clientOutput.write(("Content-Type: application/json\r\n").getBytes());
            clientOutput.write(("\r\n").getBytes());
            clientOutput.write(json.getBytes());
            clientOutput.write(("\r\n\r\n").getBytes());
            clientOutput.flush();
            client.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Request record to hold the requests send from varius sources
     * @param method Method that the request used to be sent
     * @param target Target URL
     * @param httpVersion Version of the HTTP protocol used
     * @param json String of the json object recived
     */
    static record Request(String method, String target, String httpVersion, String json) implements Serializable {}
    
    /**
     * Response to serialize and send to initial request sender
     * @param code Corresponding code relative to reception status
     * @param body More info about the reception (usually uniform). Primarily to display on interface, and log
     */
    static record Response(String code, String body) implements Serializable {}
    
    /**
     * Message (or action / command) to send to the respective target.
     * @param target Targeted subsystem of the action
     * @param level Respective level of the action
     * @param title Title of the action (primarily for interface)
     * @param body Command or action to execute
     */
    static record Message(Targets target, Levels level, String title, String body) implements Serializable {}
    
    /**
     * Action / command reception points
     */
    enum Targets {
        /**
         * Sends a command or action to the interface
         */
        INTERFACE,
        /**
         * Sends a command or action to the AutoNav server
         */
        SERVER
    }
    
    /**
     * Levels of the request, correlating to process priority
     */
    enum Levels {
        /**
         * Basic level, lowest process priority 
         */
        INFO,
        /**
         * Basic level, second lowest process priority
         */
        SUCCESS,
        /**
         * Intermediate level, second highest process priority
         */
        WARN,
        /**
         * Highest level, highest process priority; could mean AutoNav server or Interpreter shutdown
         */
        FATAL
    }
}