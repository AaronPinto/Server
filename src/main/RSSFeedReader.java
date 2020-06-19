package main;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.services.gmail.Gmail;
import com.google.common.base.Charsets;
import main.util.GoogleMail;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
                        StringBuilder sb = new StringBuilder();
                        String line;

                        outer:
                        while ((line = in.readLine()) != null) {
                            if (line.contains("<channel>")) {
                                sb.append(line);

                                while ((line = in.readLine()) != null) {
                                    sb.append(line);

                                    if (line.contains("</channel>")) {
                                        break outer;
                                    }
                                }
                            }
                        }

                        var docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                        var nList = docBuilder.parse(new InputSource(new StringReader(sb.toString()))).getElementsByTagName("item");

                        for (int i = 0; i < nList.getLength(); i++) {
                            var element = (Element) nList.item(i);

                            String title = element.getElementsByTagName("title").item(0).getTextContent();
                            String link = element.getElementsByTagName("link").item(0).getTextContent();
                            NodeList temp = element.getElementsByTagName("category");
                            var categories = IntStream.range(0, temp.getLength()).mapToObj(temp::item).map(Node::getTextContent)
                                    .collect(Collectors.toList());

                            if (categories.stream().anyMatch(s -> s.contains("Windows Insider Program")) && prevLinks.stream()
                                    .noneMatch(s -> s.equals(link))) {
                                System.out.println(title + " " + LocalDateTime.now());

                                pw.println(link);
                                pw.flush();

                                prevLinks = Files.readAllLines(Paths.get(linksPath), Charsets.UTF_8);

                                GoogleMail.sendMessage(gmail, "me", GoogleMail.createEmail(recipient, recipient, title, link));
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
