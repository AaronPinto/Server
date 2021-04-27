package server;

import com.google.api.services.gmail.Gmail;
import com.google.common.base.Charsets;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import server.util.GoogleMail;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class contains an RSS feed reader I created which sends me an email when there's a new Windows Insider build published
 */
public class RSSFeedReader {
    private RSSFeedReader() {
        // Prevent class from being instantiated
    }

    static void start(final String recipient, final String sender) {
        new Thread(() -> {
            try {
                final String titlesPath = "prevTitles.txt";
                final PrintWriter pw = new PrintWriter(new FileWriter(titlesPath, true));
                final Gmail gmail = new Gmail.Builder(Server.HTTP_TRANSPORT, Server.JSON_FACTORY, Server.authorize(sender))
                        .setApplicationName(Server.APPLICATION_NAME).build();
                final URL url = new URL("https://blogs.windows.com/feed/");
                List<String> prevTitles = Files.readAllLines(Paths.get(titlesPath), Charsets.UTF_8);

                while (true) {
                    try {
                        var in = new BufferedReader(new InputStreamReader(url.openStream(), Charsets.UTF_8));
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

                            if (categories.stream().anyMatch(s -> s.contains("Windows Insider Program")) && prevTitles.stream()
                                    .noneMatch(s -> s.equals(title))) {
                                System.out.println(LocalDateTime.now() + " " + title);

                                GoogleMail.sendMessage(gmail, "me", GoogleMail.createEmail(recipient, sender, title, link));

                                pw.println(title);
                                pw.flush();

                                prevTitles = Files.readAllLines(Paths.get(titlesPath), Charsets.UTF_8);
                            }
                        }

                        in.close();
                        System.out.println(LocalDateTime.now());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    Thread.sleep(5 * 60 * 1000);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
