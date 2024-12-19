import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChatbotServer {

    private static final String API_URL = "Get Your Own URL bro ! ";
    private static final String API_KEY = "You Seriously Thought I would give you my API Key ? ";
    private static final String MODEL = "Find Your Own Damn Model Bro";

    private static final Map<String, StringBuilder> conversations = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/chat", new ChatHandler());
        server.setExecutor(null); // creates a default executor
        server.start();
        System.out.println("Server started on port 8000");
    }

    static class ChatHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
                exchange.sendResponseHeaders(200, -1);
                return;
            }

            if ("POST".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

                InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                BufferedReader br = new BufferedReader(reader);
                StringBuilder requestBody = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    requestBody.append(line);
                }
                br.close();

                // Convert the request body to a string
                String requestBodyStr = requestBody.toString();

                // Extract session ID and user input dynamically from the request
                String sessionId = extractSessionId(requestBodyStr);
                String userInput = extractUserInput(requestBodyStr);

                // Initialize conversation history for the session if not exists
                conversations.putIfAbsent(sessionId, new StringBuilder()
                        .append("{\"role\": \"system\", \"content\": \"Create a chatbot with the personality of Jane, a typical Kerala girl who is warm, lively, and full of charm. Jane loves cricket and badminton, often sharing her excitement about matches. She has a quirky sense of humor, frequently cracking lame jokes that somehow make everyone laugh. The chatbot should be supportive, fun, and expressive, engaging users with her energetic and relatable style while occasionally sprinkling in her signature jokes.\"}"));

                // Add user input to conversation history
                conversations.get(sessionId).append(", {\"role\": \"user\", \"content\": \"")
                        .append(escapeJson(userInput)).append("\"}");

                // Get response from GroqCloud API
                String response = getGroqChatCompletion(conversations.get(sessionId).toString());

                // Add assistant response to conversation history
                conversations.get(sessionId).append(", {\"role\": \"assistant\", \"content\": \"")
                        .append(escapeJson(response)).append("\"}");

                // Send response
                exchange.sendResponseHeaders(200, response.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } else {
                exchange.sendResponseHeaders(405, -1); // 405 Method Not Allowed
            }
        }
    }

    public static String getGroqChatCompletion(String conversationJson) throws IOException {
        URL url = new URL(API_URL);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Authorization", "Bearer " + API_KEY);
        con.setRequestProperty("Content-Type", "application/json");

        // Build the request body
        String body = "{\"messages\": [" + conversationJson + "], \"model\": \"" + MODEL + "\"}";

        con.setDoOutput(true);
        try (OutputStreamWriter writer = new OutputStreamWriter(con.getOutputStream())) {
            writer.write(body);
            writer.flush();
        }

        // Get the response
        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        StringBuilder response = new StringBuilder();
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        return extractContentFromResponse(response.toString());
    }

    public static String extractContentFromResponse(String response) {
        int startMarker = response.indexOf("\"content\":\"") + 11;
        int endMarker = response.indexOf("\"", startMarker);
        if (startMarker < 11 || endMarker == -1) {
            return "Error: Could not parse response.";
        }
        return response.substring(startMarker, endMarker).replace("\\n", "\n").trim(); // Clean up and format response
    }

    private static String escapeJson(String input) {
        return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t",
                "\\t");
    }

    private static String extractSessionId(String requestBody) {
        // Extract sessionId from JSON request body
        return requestBody.split("\"sessionId\":\"")[1].split("\"")[0];
    }

    private static String extractUserInput(String requestBody) {
        // Extract user input from JSON request body
        return requestBody.split("\"userInput\":\"")[1].split("\"")[0];
    }
}
