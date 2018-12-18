import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.services.gmail.Gmail;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class AddAccountCreds {
	public static void main(String[] args) throws GeneralSecurityException, IOException {
		NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
		new Gmail.Builder(HTTP_TRANSPORT, RSSFeedReader.JSON_FACTORY, RSSFeedReader.getCredentials(HTTP_TRANSPORT, "pintoa9"))
				.setApplicationName(RSSFeedReader.APPLICATION_NAME).build();
	}
}