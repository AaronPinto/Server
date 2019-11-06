import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.DataStore;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.common.base.Charsets;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

class RSSFeedReader {
    static final String APPLICATION_NAME = "Gmail API Java Quickstart";
    static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    static final String CREDENTIALS_FOLDER = "credentials"; // Directory to store user credentials.
    /**
     * Global instance of the scopes required by this file. If modifying these scopes, delete your previously saved credentials/ folder.
     */
    private static final String CLIENT_SECRET_DIR = "client_secret.json";

    static void start() {
        new Thread(() -> {
            try {
                final String linksPath = "prevLinks.txt";
                final PrintWriter pw = new PrintWriter(new FileWriter(linksPath, true));
                final String recipient = Files.readAllLines(Paths.get("ignore_emails.txt"), Charsets.UTF_8).get(0) + "@gmail.com";
                final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
                final String user = Objects.requireNonNull(new File(RSSFeedReader.CREDENTIALS_FOLDER).listFiles())[2].getName();
                final Gmail gmail = new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT, user))
                    .setApplicationName(APPLICATION_NAME).build();
                List<String> prevLinks = Files.readAllLines(Paths.get(linksPath), Charsets.UTF_8);

                while (true) {
                    try {
                        BufferedReader in = new BufferedReader(new InputStreamReader(new URL("https://blogs.windows.com/feed/")
                            .openStream()));
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

    /**
     * Creates an authorized Credential object.
     *
     * @param HTTP_TRANSPORT The network HTTP Transport.
     *
     * @return An authorized Credential object.
     *
     * @throws IOException If there is no client_secret.
     */
    static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT, String user) throws IOException {
        // Load client secrets.
        GoogleClientSecrets clientSecrets = GoogleClientSecrets
            .load(JSON_FACTORY, new InputStreamReader(RSSFeedReader.class.getResourceAsStream(CLIENT_SECRET_DIR)));

        // Build flow and trigger user authorization request.
        DataStore<StoredCredential> dataStore = new FileDataStoreFactory(new java.io.File(CREDENTIALS_FOLDER)).getDataStore(user);
        ArrayList<String> scopes = new ArrayList<>(GmailScopes.all());
        scopes.addAll(DriveScopes.all());
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, scopes)
            .setCredentialDataStore(dataStore).setAccessType("offline").build();
        return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
    }
}
