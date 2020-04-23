package main.util;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.services.gmail.Gmail;
import main.Server;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class AddAccountCreds {
    private AddAccountCreds() {
        // Prevent class from being instantiated
    }

    public static void addCreds(String username) throws GeneralSecurityException, IOException {
        NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        new Gmail.Builder(HTTP_TRANSPORT, Server.JSON_FACTORY, Server.getCredentials(HTTP_TRANSPORT, username))
                .setApplicationName(Server.APPLICATION_NAME).build();
    }

    public static void main(String[] args) throws GeneralSecurityException, IOException {
        addCreds("insert username here");
    }
}
