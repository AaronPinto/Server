package server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.google.api.services.gmail.Gmail;
import jakarta.mail.MessagingException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import server.util.GoogleMail;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * This class contains an RSS feed reader I created which emails me when there's a new Windows Insider build published
 */
public class RSSFeedReader {
    private static final TypeReference<ArrayList<RSSEventFilter>> listOfRSSEvents = new TypeReference<>() {};
    private static Gmail gmail;
    private static String recipient;
    private static String sender;

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
                final URL[] urls = {URI.create("https://blogs.windows.com/windows-insider/feed/").toURL(),
                        URI.create("https://blogs.windows.com/feed/").toURL()};
                final String eventsPath = "prevWindowsEvents.json";

                try {
                    Files.createFile(Paths.get(eventsPath));
                } catch (FileAlreadyExistsException ignored) {
                }

                parseRSS(urls, eventsPath, "Windows Insider Program");
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                System.out.println("Exiting Windows Insider RSS!");
            }
        }).start();
    }

    static void parseRSS(final URL[] urls, final String eventsPath, final String category) throws InterruptedException, IOException {
        final ObjectMapper mapper = new ObjectMapper();
        List<RSSEventFilter> prevEvents;
        try (Reader reader = new FileReader(eventsPath)) {
            prevEvents = mapper.readValue(reader, listOfRSSEvents);
        } catch (MismatchedInputException ignored) {
            prevEvents = new ArrayList<>();
        }

        while (true) {
            try {
                StringBuilder sb = readAndCombineRSSFeeds(urls);

                // Parse combined RSS feed, so we only do parsing once
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
                    mapper.writeValue(writer, prevEvents);
                }

                System.out.println(LocalDateTime.now());
            } catch (ParserConfigurationException | SAXException | IOException | MessagingException e) {
                e.printStackTrace();
            }

            Thread.sleep(5 * 60 * 1000);
        }
    }

    public static StringBuilder readAndCombineRSSFeeds(final URL[] streams) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;

        // Read RSS feeds between channel tags and combine
        for (int i = 0; i < streams.length; i++) {
            try (var in = new BufferedReader(new InputStreamReader(streams[i].openStream(), StandardCharsets.UTF_8))) {
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
                if (i == streams.length - 1) {
                    sb.append(line);
                }
            }
        }

        return sb;
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
