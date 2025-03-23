package org.example.producer;

import com.weather.avro.Weather;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Properties;


public class WeatherProducer {
    private static final String TOPIC = "weather-topic";
    private static final String API_KEY = "f662e382e4eeede1931aba50b167acbc";
    private final KafkaProducer<String, Weather> producer;

    public WeatherProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "<HOST_IP>:9092,<HOST_IP>:9093,<HOST_IP>:9094");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());
        props.put("schema.registry.url", "http://<HOST_IP>:8081");
        props.put("security.protocol", "PLAINTEXT");

        this.producer = new KafkaProducer<>(props);
    }

    public void produceWeatherData(String location) {
        try {
            String response = fetchWeatherData(location);
            JSONObject json = new JSONObject(response);

            // Handle API errors
            if (json.has("error")) {
                JSONObject error = json.getJSONObject("error");
                throw new RuntimeException("WeatherAPI Error: " + error.getString("info"));
            }

            // Validate response structure
            if (!json.has("current")) {
                throw new JSONException("Missing 'current' object in API response");
            }

            JSONObject current = json.getJSONObject("current");
            double temperature = current.getDouble("temperature");
            double humidity = current.getDouble("humidity");
            String suggestion = getSuggestion(temperature);

            Weather weather = Weather.newBuilder()
                    .setLocation(location)
                    .setTemperature(temperature)
                    .setHumidity(humidity)
                    .setSuggestion(suggestion)
                    .build();

            ProducerRecord<String, Weather> record = new ProducerRecord<>(TOPIC, location, weather);

            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    System.err.println("Failed to send record: " + exception.getMessage());
                } else {
                    System.out.println("Sent weather data for " + location +
                            " to partition " + metadata.partition());
                }
            });
            producer.flush();

        } catch (Exception e) {
            System.err.println("Failed to process weather data for " + location + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String fetchWeatherData(String location) throws Exception {
        String encodedLocation = URLEncoder.encode(location, StandardCharsets.UTF_8.name());
        String apiUrl = "http://api.weatherstack.com/current?access_key=" + API_KEY + "&query=" + encodedLocation;

        HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
        conn.setRequestMethod("GET");

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            BufferedReader errorReader = new BufferedReader(
                    new InputStreamReader(conn.getErrorStream())
            );
            StringBuilder errorResponse = new StringBuilder();
            String line;
            while ((line = errorReader.readLine()) != null) {
                errorResponse.append(line);
            }
            errorReader.close();
            throw new RuntimeException("API request failed: " + errorResponse.toString());
        }

        BufferedReader in = new BufferedReader(
                new InputStreamReader(conn.getInputStream())
        );
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            response.append(line);
        }
        in.close();

        return response.toString();
    }

    private String getSuggestion(double temperature) {
        if (temperature > 30) return "Stay cool! Drink plenty of water";
        if (temperature > 20) return "Enjoy the pleasant weather";
        if (temperature > 10) return "Wear a light jacket";
        return "It's cold outside! Bundle up";
    }

    public void close() {
        producer.close();
    }
}

//public class WeatherProducer {
//    private static final String TOPIC = "weather-topic";
//    private static final String API_KEY = "<API_KEY>";
//
//    public static void main(String[] args) {
//        Properties props = new Properties();
//        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "<HOST_IP>:9092,1<HOST_IP>:9093,<HOST_IP>:9094");
//        props.put("key.serializer", KafkaAvroSerializer.class.getName());
//        props.put("value.serializer", KafkaAvroSerializer.class.getName());
//        props.put("schema.registry.url", "http://<HOST_IP>:8081");
//        props.put("security.protocol", "PLAINTEXT");
//        props.put("client.dns.lookup", "use_all_dns_ips"); // Resolve all DNS entries
//
//
//        Producer<String, Weather> producer = new KafkaProducer<>(props);
//
//        try {
//            String location = "New York";
//            String response = fetchWeatherData(location);
//
//            // Parse the response with JSONObject
//            JSONObject json = new JSONObject(response);
//            double temperature = json.getJSONObject("current").getDouble("temperature");
//            double humidity = json.getJSONObject("current").getDouble("humidity");
//            String suggestion = getSuggestion(temperature);
//
//            Weather weather = Weather.newBuilder()
//                    .setLocation(location)
//                    .setTemperature(temperature)
//                    .setHumidity(humidity)
//                    .setSuggestion(suggestion)
//                    .build();
//
//            // Create a ProducerRecord
//            ProducerRecord<String, Weather> record = new ProducerRecord<>(TOPIC, location, weather);
//
//            // Send record with a callback
//            producer.send(record, (metadata, exception) -> {
//                if (exception == null) {
//                    System.out.println("Message sent: " + weather);
//                } else {
//                    exception.printStackTrace();
//                }
//            });
//
//            producer.flush();
//        } catch (Exception e) {
//            e.printStackTrace();
//        } finally {
//            producer.close();
//        }
//    }
//
//    private static String fetchWeatherData(String location) throws Exception {
//        String apiUrl = "http://api.weatherstack.com/current?access_key=" + API_KEY + "&query=" + location;
//        HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
//        conn.setRequestMethod("GET");
//
//        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
//        StringBuilder response = new StringBuilder();
//        String line;
//
//        while ((line = in.readLine()) != null) {
//            response.append(line);
//        }
//        in.close();
//
//        return response.toString();
//    }
//
//    private static String getSuggestion(double temperature) {
//        if (temperature > 30) {
//            return "Drink cold beverages";
//        } else if (temperature < 10) {
//            return "Drink hot beverages";
//        } else {
//            return "Room temperature drinks are fine";
//        }
//    }
//}
