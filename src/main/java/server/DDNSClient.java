package server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * @formatter:off
 * Dynamic DNS Client for my google domain
 * https://support.google.com/domains/answer/6147083
 * @formatter:on
 */
public class DDNSClient {
    private static final TypeReference<ArrayList<String>> listOfIPAddrs = new TypeReference<>() {};

    private DDNSClient() {
        // Prevent class from being instantiated
    }

    static void start(String user, String pass, String host) {
        new Thread(() -> {
            try {
                final var client = HttpClient.newHttpClient();
                final var postReq = HttpRequest.newBuilder(URI.create("https://domains.google.com/nic/update?hostname=" + host))
                        .header("Authorization", "Basic " + Base64.getEncoder().encodeToString((user + ":" + pass).getBytes()))
                        .POST(HttpRequest.BodyPublishers.noBody()).build();
                final var getReq = HttpRequest.newBuilder(URI.create("https://domains.google.com/checkip")).build();

                final String eventsPath = "prevIPAddresses.json";

                try {
                    Files.createFile(Paths.get(eventsPath));
                } catch (FileAlreadyExistsException ignored) {
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                final ObjectMapper mapper = new ObjectMapper();
                List<String> prevIPs;
                try (Reader reader = new FileReader(eventsPath)) {
                    prevIPs = mapper.readValue(reader, listOfIPAddrs);
                } catch (IOException ignored) {
                    prevIPs = new ArrayList<>();
                }

                while (true) {
                    try {
                        var getResp = client.send(getReq, HttpResponse.BodyHandlers.ofString());

                        if (getResp.statusCode() == 200 && prevIPs.stream().noneMatch(s -> s.equals(getResp.body()))) {
                            var postResp = client.send(postReq, HttpResponse.BodyHandlers.ofString());

                            if (postResp.statusCode() == 200) {
                                String[] splitResp = postResp.body().split(" ");
                                String type = splitResp[0];

                                System.out.println(LocalDateTime.now() + " " + postResp.statusCode() + " " + postResp.body());

                                if (type.equals("good") || type.equals("nochg")) {
                                    prevIPs.add(splitResp[1]);

                                    try (Writer writer = new FileWriter(eventsPath)) {
                                        mapper.writeValue(writer, prevIPs);
                                    }
                                } else {
                                    Thread.sleep(4 * 60 * 1000);
                                }
                            }
                        }
                    } catch (IOException | InterruptedException e) {
                        e.printStackTrace();
                        Thread.sleep(4 * 60 * 1000);
                    }

                    Thread.sleep(60 * 1000);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }
}
