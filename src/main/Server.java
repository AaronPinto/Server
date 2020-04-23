package main;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.DataStore;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.gmail.GmailScopes;
import com.google.common.base.Charsets;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

public class Server {
    public static final String APPLICATION_NAME = "Gmail API Java Quickstart";
    public static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    public static final String CREDENTIALS_FOLDER = "credentials"; // Directory to store user credentials

    /**
     * Global instance of the scopes required by this file. If modifying these scopes, delete your previously saved credentials folder.
     */
    private static final String CLIENT_SECRET_DIR = "/client_secret.json";

    public static void main(String[] args) {
        try {
            RSSFeedReader.start();
            CLI.start();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
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
    public static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT, String user) throws IOException {
        // Load client secrets.
        GoogleClientSecrets clientSecrets = GoogleClientSecrets
                .load(JSON_FACTORY, new InputStreamReader(Server.class.getResourceAsStream(CLIENT_SECRET_DIR)));

        // Build flow and trigger user authorization request.
        DataStore<StoredCredential> dataStore = new FileDataStoreFactory(new File(CREDENTIALS_FOLDER)).getDataStore(user);
        ArrayList<String> scopes = new ArrayList<>(GmailScopes.all());
        scopes.addAll(DriveScopes.all());
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, scopes)
                .setCredentialDataStore(dataStore).setAccessType("offline").build();
        return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
    }

    public static ArrayList<String> getUsernames() throws IOException, SecurityException, NullPointerException {
        ArrayList<String> fileNames = Arrays.stream(Objects.requireNonNull(new File(CREDENTIALS_FOLDER).listFiles())).map(File::getName)
                .collect(Collectors.toCollection(ArrayList::new));
        fileNames.removeAll(Files.readAllLines(Paths.get("ignore_emails.txt"), Charsets.UTF_8));
        return fileNames;
    }
}
