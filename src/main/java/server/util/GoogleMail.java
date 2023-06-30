package server.util;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.common.io.BaseEncoding;
import jakarta.mail.Message.RecipientType;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Properties;

public final class GoogleMail {
    private GoogleMail() {
        // Prevent class from being instantiated
    }

    public static MimeMessage createEmail(String to, String from, String subject, String bodyText) throws MessagingException {
        MimeMessage email = new MimeMessage(Session.getDefaultInstance(new Properties(), null));
        email.setFrom(new InternetAddress(from));
        email.addRecipient(RecipientType.TO, new InternetAddress(to));
        email.setSubject(subject);
        email.setText(bodyText);
        return email;
    }

    /**
     * Create a message from an email.
     *
     * @param emailContent Email to be set to raw of message
     *
     * @return a message containing a base64url encoded email
     *
     * @throws IOException        IOException
     * @throws MessagingException MessagingException
     */
    public static Message createMessageWithEmail(MimeMessage emailContent) throws MessagingException, IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        emailContent.writeTo(buffer);
        String encodedEmail = BaseEncoding.base64Url().omitPadding().encode(buffer.toByteArray());
        return new Message().setRaw(encodedEmail);
    }

    /**
     * Send an email from the user's mailbox to its recipient.
     *
     * @param service      Authorized Gmail API instance.
     * @param userId       User's email address. The special value "me" can be used to indicate the authenticated user.
     * @param emailContent Email to be sent.
     *
     * @return The sent message
     *
     * @throws MessagingException MessagingException
     * @throws IOException        IOException
     */
    public static Message sendMessage(Gmail service, String userId, MimeMessage emailContent) throws MessagingException, IOException {
        Message message = createMessageWithEmail(emailContent);
        message = service.users().messages().send(userId, message).execute();
        return message;
    }
}
