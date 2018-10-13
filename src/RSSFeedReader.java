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
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.common.base.Charsets;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

public class RSSFeedReader {
	static void start() {
		new Thread(() -> {
			try {
				PrintWriter pw = new PrintWriter(new FileWriter("prevTitles.txt", true));
				List<String> list = Files.readAllLines(Paths.get("prevTitles.txt"), Charsets.UTF_8);
				final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
				final Gmail gmail = new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT, "aaronp110"))
						.setApplicationName(APPLICATION_NAME).build();

				while(true) {
					try {
						BufferedReader in = new BufferedReader(new InputStreamReader(new URL("https://blogs.windows.com/feed/").openStream()));
						String line;
						while((line = in.readLine()) != null) {
							int titleStartIndex = 0, titleEndIndex = 0;
							while(titleStartIndex >= 0) {
								titleStartIndex = line.indexOf("<title>", titleEndIndex);
								if(titleStartIndex >= 0) {
									titleEndIndex = line.indexOf("</title>", titleStartIndex);
									String title = line.substring(titleStartIndex + "<title>".length(), titleEndIndex);
									if(title.contains("Announcing Windows 10 Insider Preview Build") && list.stream().noneMatch(s -> s.equals(title))) {
										System.out.println(title + " " + LocalDateTime.now());
										pw.println(title);
										pw.flush();
										list = Files.readAllLines(Paths.get("prevTitles.txt"), Charsets.UTF_8);
										GoogleMail.sendMessage(gmail, "me", GoogleMail.createEmail(
												"aaronp110@gmail.com", "aaronp110@gmail.com", title,
												"https://blogs.windows.com/windowsexperience/tag/windows-insider-program/"));
									}
								}
							}
						}
						in.close();
						System.out.println(LocalDateTime.now());
						Thread.sleep((long) (4 * 60 * 1000));//Min seconds milliseconds
					} catch(Exception e) {
						e.printStackTrace();
					}
					Thread.sleep(60 * 1000);
				}
			} catch(Exception e) {
				e.printStackTrace();
			}
		}).start();
	}

	static final String APPLICATION_NAME = "Gmail API Java Quickstart";
	static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
	static final String CREDENTIALS_FOLDER = "credentials";//Directory to store user credentials.

	/**
	 * Global instance of the scopes required by this file.
	 * If modifying these scopes, delete your previously saved credentials/ folder.
	 */
	private static final String CLIENT_SECRET_DIR = "client_secret.json";

	/**
	 * Creates an authorized Credential object.
	 *
	 * @param HTTP_TRANSPORT The network HTTP Transport.
	 * @return An authorized Credential object.
	 * @throws IOException If there is no client_secret.
	 */
	static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT, String user) throws IOException {
		//Load client secrets.
		InputStream in = RSSFeedReader.class.getResourceAsStream(CLIENT_SECRET_DIR);
		GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

		//Build flow and trigger user authorization request.
		DataStore<StoredCredential> dataStore = new FileDataStoreFactory(new java.io.File(CREDENTIALS_FOLDER)).getDataStore(user);
		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, GmailScopes.all())
				.setCredentialDataStore(dataStore).setAccessType("offline").build();
		return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
	}
}