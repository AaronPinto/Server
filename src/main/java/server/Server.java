package server;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Charsets;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.gmail.GmailScopes;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Server {
    public static final String APPLICATION_NAME = "My Server Utilities";
    public static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    public static final String CREDENTIALS_FOLDER = "credentials"; // Directory to store user credentials
    public static NetHttpTransport HTTP_TRANSPORT;

    /**
     * Global instance of the scopes required by this file. If modifying these scopes, delete your previously saved credentials folder.
     */
    private static final List<String> SCOPES = List.of(GmailScopes.GMAIL_SEND, DriveScopes.DRIVE_FILE);
    private static final String CLIENT_SECRET_DIR = "/client_secret.json";
    private static FileDataStoreFactory DATA_STORE_FACTORY;

    static {
        try {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            DATA_STORE_FACTORY = new FileDataStoreFactory(new File(CREDENTIALS_FOLDER));
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static void main(String[] args) {
        try {
            isValidEmailAddress(args[0]);
            RSSFeedReader.start(args[0], args[1]);
            CLI.start();
            if (args.length == 5) {
                System.out.println("Running DDNS Client...");
                DDNSClient.start(args[2], args[3], args[4]);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private Server() {
        // Prevent class from being instantiated
    }

    // https://stackoverflow.com/a/5931718/6713362
    public static void isValidEmailAddress(String email) throws AddressException {
        new InternetAddress(email).validate();
    }

    /**
     * Creates an authorized Credential object.
     *
     * @param user The name of the user to authorize.
     *
     * @return An authorized Credential object.
     *
     * @throws IOException If there is no client_secret.
     */
    public static Credential authorize(String user) throws IOException {
        // Load client secrets.
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY,
                new InputStreamReader(Objects.requireNonNull(Server.class.getResourceAsStream(CLIENT_SECRET_DIR))));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setCredentialDataStore(DATA_STORE_FACTORY.getDataStore(user)).setAccessType("offline").build();
        return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
    }

    /**
     * Get usernames for all authorized users
     *
     * @return An ArrayList of all authorized users
     *
     * @throws IOException          If ignore_emails.txt doesn't exist or can't be found
     * @throws SecurityException    If a security manager exists and has a problem
     * @throws NullPointerException If the credentials folder doesn't exist or can't be found
     */
    public static ArrayList<String> getUsernames() throws IOException, SecurityException, NullPointerException {
        ArrayList<String> fileNames = Arrays.stream(Objects.requireNonNull(new File(CREDENTIALS_FOLDER).listFiles())).map(File::getName)
                .collect(Collectors.toCollection(ArrayList::new));
        fileNames.removeAll(Files.readAllLines(Paths.get("ignore_emails.txt"), Charsets.UTF_8));
        return fileNames;
    }
}
