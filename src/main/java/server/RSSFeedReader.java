package server;

import com.google.api.services.gmail.Gmail;
import com.google.common.base.Charsets;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import server.util.GoogleMail;

import javax.mail.MessagingException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * This class contains an RSS feed reader I created which emails me when there's a new Windows Insider build published
 */
public class RSSFeedReader {
    private static Gmail gmail;
    private static String recipient;
    private static String sender;

    private static final Type listOfRSSEvents = new TypeToken<ArrayList<RSSEventFilter>>() {}.getType();

    private RSSFeedReader() {
        // Prevent class from being instantiated
    }

    static void start(final String recipient, final String sender) throws IOException {
        RSSFeedReader.recipient = recipient;
        RSSFeedReader.sender = sender;
        gmail = new Gmail.Builder(Server.HTTP_TRANSPORT, Server.JSON_FACTORY, Server.authorize(sender))
                .setApplicationName(Server.APPLICATION_NAME).build();

        // Create Windows Insider Build RSS Feed Reader
        new Thread(() -> {
            try {
                final URL[] urls = {new URL("https://blogs.windows.com/windows-insider/feed/"), new URL("https://blogs.windows.com/feed/")};
                final String eventsPath = "prevWindowsEvents.json";

                try {
                    Files.createFile(Paths.get(eventsPath));
                } catch (FileAlreadyExistsException ignored) {
                }

                parseRSS(urls, eventsPath, "Windows Insider Program");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        // Create BestBuy Nvidia RSS Feed Reader
        new Thread(() -> {
            try {
                final URL[] urls = {new URL("https://blog.bestbuy.ca/category/best-buy/feed")};
                final String eventsPath = "prevNvidiaEvents.json";

                try {
                    Files.createFile(Paths.get(eventsPath));
                } catch (FileAlreadyExistsException ignored) {
                }

                parseRSS(urls, eventsPath, "nvidia");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    static void parseRSS(final URL[] urls, final String eventsPath, final String category) throws InterruptedException, IOException {
        final Gson gson = new Gson();
        List<RSSEventFilter> prevEvents;
        try (Reader reader = new FileReader(eventsPath)) {
            prevEvents = Objects.requireNonNullElse(gson.fromJson(reader, listOfRSSEvents), new ArrayList<>());
        }

        while (true) {
            try {
                StringBuilder sb = new StringBuilder();
                String line;

                // Read RSS feeds between channel tags and combine
                for (int i = 0; i < urls.length; i++) {
                    var in = new BufferedReader(new InputStreamReader(urls[i].openStream(), Charsets.UTF_8));

                    outer:
                    while ((line = in.readLine()) != null) {
                        if (line.contains("<channel>")) {
                            if (i == 0) {
                                sb.append(line);
                            }

                            while ((line = in.readLine()) != null) {
                                if (line.contains("</channel>")) {
                                    break outer;
                                }

                                sb.append(line);
                            }
                        }
                    }

                    // Add </channel> tag on last URL only
                    if (i == urls.length - 1) {
                        sb.append(line);
                    }

                    in.close();
                }

                // Parse combined RSS feed
                var docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                var nList = docBuilder.parse(new InputSource(new StringReader(sb.toString()))).getElementsByTagName("item");

                for (int i = 0; i < nList.getLength(); i++) {
                    var element = (Element) nList.item(i);

                    String title = element.getElementsByTagName("title").item(0).getTextContent();
                    String link = element.getElementsByTagName("link").item(0).getTextContent();
                    String pubDate = element.getElementsByTagName("pubDate").item(0).getTextContent();
                    NodeList temp = element.getElementsByTagName("category");
                    var categories = IntStream.range(0, temp.getLength()).mapToObj(temp::item).map(Node::getTextContent).toList();

                    // Filter elements
                    if (categories.stream().anyMatch(s -> s.contains(category)) &&
                            prevEvents.stream().noneMatch(s -> s.pubDateEquals(pubDate) && s.titleEquals(title))) {
                        System.out.println(LocalDateTime.now() + " " + title);

                        GoogleMail.sendMessage(gmail, "me", GoogleMail.createEmail(recipient, sender, title, link));

                        prevEvents.add(new RSSEventFilter(title, pubDate));
                    }
                }

                try (Writer writer = new FileWriter(eventsPath)) {
                    gson.toJson(prevEvents, writer);
                }

                System.out.println(LocalDateTime.now());
            } catch (MessagingException | ParserConfigurationException | SAXException | IOException e) {
                e.printStackTrace();
            }

            Thread.sleep(5 * 60 * 1000);
        }
    }

    record RSSEventFilter(String title, String pubDate) implements Serializable {
        boolean titleEquals(String t) {
            return title.equals(t);
        }

        boolean pubDateEquals(String p) {
            return pubDate.equals(p);
        }
    }
}
