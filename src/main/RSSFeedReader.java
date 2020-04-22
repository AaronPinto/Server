package main;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.services.gmail.Gmail;
import com.google.common.base.Charsets;
import main.util.GoogleMail;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public class RSSFeedReader {
    static void start() {
        new Thread(() -> {
            try {
                final String linksPath = "prevLinks.txt";
                final PrintWriter pw = new PrintWriter(new FileWriter(linksPath, true));
                final String recipient = Files.readAllLines(Paths.get("ignore_emails.txt"), Charsets.UTF_8).get(0) + "@gmail.com";
                final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
                final String user = Objects.requireNonNull(new File(Server.CREDENTIALS_FOLDER).listFiles())[2].getName();
                final Gmail gmail = new Gmail.Builder(HTTP_TRANSPORT, Server.JSON_FACTORY, Server.getCredentials(HTTP_TRANSPORT, user))
                        .setApplicationName(Server.APPLICATION_NAME).build();
                List<String> prevLinks = Files.readAllLines(Paths.get(linksPath), Charsets.UTF_8);

                while (true) {
                    try {
                        var in = new BufferedReader(new InputStreamReader(new URL("https://blogs.windows.com/feed/").openStream()));
                        String line;

                        while ((line = in.readLine()) != null) {
                            int linkStartIndex, linkEndIndex = 0;
                            linkStartIndex = line.indexOf("<link>", linkEndIndex);

                            if (linkStartIndex >= 0) {
                                linkEndIndex = line.indexOf("</link>", linkStartIndex);
                                String link = line.substring(linkStartIndex + "<link>".length(), linkEndIndex);

                                if (link.contains("announcing-windows-10-insider-preview-build") && link
                                        .contains("windowsexperience") && prevLinks.stream().noneMatch(s -> s.equals(link))) {
                                    String title = link.substring(link.indexOf("announcing"), link.length() - 1);
                                    System.out.println(title + " " + LocalDateTime.now());

                                    pw.println(link);
                                    pw.flush();

                                    prevLinks = Files.readAllLines(Paths.get(linksPath), Charsets.UTF_8);

                                    GoogleMail.sendMessage(gmail, "me", GoogleMail.createEmail(recipient, recipient, title, link));
                                }
                            }
                        }

                        in.close();
                        System.out.println(LocalDateTime.now());
                        Thread.sleep(4 * 60 * 1000);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    Thread.sleep(2 * 60 * 1000);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
