import com.google.api.client.util.Charsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import server.RSSFeedReader;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RSSFeedReaderTest {
    @Test
    @DisplayName("Test Windows Blog feed combination")
    public void windowsBlogTest() throws IOException, URISyntaxException {
        final URL[] urls = {getClass().getResource("blogs.windows.com_windows-insider_feed.xml"),
                getClass().getResource("blogs.windows.com_feed.xml")};
        final var sb = RSSFeedReader.readAndCombineRSSFeeds(urls);

        final URI test = Objects.requireNonNull(getClass().getResource("blogs.windows.com_combination_result.xml")).toURI();
        final String result = Files.readString(Path.of(test), Charsets.UTF_8);

        assertEquals(result, sb.toString());
    }

    @Test
    @DisplayName("Test BestBuy Blog feed combination")
    public void bestBuyBlogTest() throws IOException, URISyntaxException {
        final URL[] urls = {getClass().getResource("blog.bestbuy.ca_category_best-buy_feed.xml")};
        final var sb = RSSFeedReader.readAndCombineRSSFeeds(urls);

        final URI test = Objects.requireNonNull(getClass().getResource("blog.bestbuy.ca_combination_result.xml")).toURI();
        final String result = Files.readString(Path.of(test), Charsets.UTF_8);

        assertEquals(result, sb.toString());
    }
}
