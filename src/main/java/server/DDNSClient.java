package server;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * @formatter:off
 * Dynamic DNS Client for my google domain
 * https://support.google.com/domains/answer/6147083
 * @formatter:on
 */
public class DDNSClient {
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
                String prevIP = "";

                while (true) {
                    try {
                        var getResp = client.send(getReq, HttpResponse.BodyHandlers.ofString());

                        if (getResp.statusCode() == 200 && !prevIP.equals(getResp.body())) {
                            var postResp = client.send(postReq, HttpResponse.BodyHandlers.ofString());

                            if (postResp.statusCode() == 200) {
                                String[] splitResp = postResp.body().split(" ");
                                String type = splitResp[0];

                                System.out.println(LocalDateTime.now() + " " + postResp.statusCode() + " " + postResp.body());

                                if (type.equals("good") || type.equals("nochg")) {
                                    prevIP = splitResp[1];
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
