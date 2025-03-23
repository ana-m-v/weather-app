package org.example.api;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.example.producer.WeatherProducer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;


public class WeatherAPI {

    private static final WeatherProducer weatherProducer = new WeatherProducer();
    private static final MongoClient mongoClient = MongoClients.create("mongodb://<HOST_IP>:27017");
    private static final MongoDatabase database = mongoClient.getDatabase("weather");
    private static final MongoCollection<Document> collection = database.getCollection("processed-data");

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        // Define context for the /weather/request endpoint
        server.createContext("/weather/request", new RequestHandler());

        // Define context for the /weather/suggestion endpoint
        server.createContext("/weather/suggestion", new SuggestionHandler());

        // Start the server
        server.setExecutor(null);
        server.start();
        System.out.println("Server started on port 8080");
    }

    // Handler for the /weather/request endpoint
    static class RequestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Set CORS headers
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

            // Handle OPTIONS request for preflight
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            // Parse query parameters
            Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI());

            // Get the location parameter
            String location = queryParams.get("location");
            if (location == null || location.isEmpty()) {
                sendResponse(exchange, 400, "Location parameter is required");
                return;
            }

            // Produce weather data for the location
            weatherProducer.produceWeatherData(location);

            // Send response
            sendResponse(exchange, 202, "Weather data request submitted for: " + location);
        }
    }

    // Handler for the /weather/suggestion endpoint
    static class SuggestionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Set CORS headers
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

            // Handle OPTIONS request for preflight
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            // Parse query parameters
            Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI());

            // Get the location parameter
            String location = queryParams.get("location");
            if (location == null || location.isEmpty()) {
                sendResponse(exchange, 400, "Location parameter is required");
                return;
            }

            // Fetch suggestion from MongoDB
            Document doc = collection.find(new Document("location", location)).first();
            if (doc == null) {
                sendResponse(exchange, 404, "No suggestion found for location: " + location);
                return;
            }

            // Send response
            sendResponse(exchange, 200, doc.toJson());
        }
    }


    // Helper method to parse query parameters
    private static Map<String, String> parseQueryParams(URI requestUri) {
        Map<String, String> queryParams = new HashMap<>();
        String query = requestUri.getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] keyValue = param.split("=");
                if (keyValue.length == 2) {
                    String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                    queryParams.put(key, value);
                }
            }
        }
        return queryParams;
    }

    // Helper method to send HTTP response
    private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, response.length());
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }
}