import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.common.base.Charsets;

import javax.mail.MessagingException;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * This class contains a command line interface that I created which contains some different commands that leverage the Gmail API
 */
class CLI {
    private static String[] cmd;
    private static String toEmail;

    static void start() {
        new Thread(() -> {
            Scanner s = new Scanner(System.in);
            while (true) {
                cmd = s.nextLine().split(" ");
                System.out.println("command is " + Arrays.toString(cmd));

                switch (cmd[0]) {
                    case "batchspam": {
                        if (cmd.length != 2) {
                            System.out
                                .println("Invalid number of arguments! There should be only one, specifying the email address to spam" +
                                    ".\n\t batchspam example@gmail.com\n\t batchspam stop");
                            break;
                        }

                        toEmail = cmd[1];

                        if (!toEmail.equals("stop")) {
                            AtomicInteger j = new AtomicInteger(0);

                            try {
                                for (String name : getUsernames()) {
                                    new Thread(() -> {
                                        try {
                                            NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
                                            Gmail gmail = new Gmail.Builder(HTTP_TRANSPORT, RSSFeedReader.JSON_FACTORY, RSSFeedReader
                                                .getCredentials(HTTP_TRANSPORT, name)).setApplicationName(RSSFeedReader.APPLICATION_NAME)
                                                .build();
                                            BatchRequest batch = gmail.batch();
                                            ArrayList<Boolean> respMessages = new ArrayList<>(100);
                                            ArrayList<Message> allMessages = new ArrayList<>(100);
                                            JsonBatchCallback<Message> callback = new JsonBatchCallback<>() {
                                                @Override
                                                public void onSuccess(Message message, HttpHeaders responseHeaders) {
                                                    respMessages.add(true);
                                                }

                                                @Override
                                                public void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) {
                                                    respMessages.add(false);
                                                    if (e.getCode() == 429) {
                                                        System.out.println(e.getMessage());
                                                    }
                                                    if (e.getMessage().contains("User-rate limit exceeded. Retry after")) {
                                                        Thread.currentThread().interrupt();
                                                    }
                                                }
                                            };

                                            double delay = 500.0, start = System.currentTimeMillis(), reqPerSec = 2.4;
                                            int local = j.getAndIncrement(), numReqSent = 0;

                                            while (!toEmail.equals("stop"))
                                                try {
                                                    // Generate a random subject so Gmail doesn't group emails in the inbox
                                                    allMessages.add(GoogleMail.createMessageWithEmail(GoogleMail
                                                        .createEmail(toEmail, "ignore@gmail.com", Long
                                                            .toHexString(Double.doubleToLongBits(Math.random() / Math.random())), String
                                                            .valueOf(local))));
                                                    local = j.getAndIncrement();

                                                    if (allMessages.size() >= 10) {
                                                        while (allMessages.size() > 0) {
                                                            for (Message m : allMessages) {
                                                                gmail.users().messages().send("me", m).queue(batch, callback);
                                                            }

                                                            numReqSent += batch.size();

                                                            try {
                                                                long ts = (long) Math.max(0, (numReqSent / reqPerSec - (System
                                                                    .currentTimeMillis() - start) / 1000.0) * 1000.0);
                                                                System.out.println("currReqPerSec: " + numReqSent / ((System
                                                                    .currentTimeMillis() - start) / 1000.0) + " " + "sleepFor: " + ts);
                                                                Thread.sleep(ts);
                                                                System.out.println("currReqPerSec: " + numReqSent / ((System
                                                                    .currentTimeMillis() - start) / 1000.0));
                                                            } catch (InterruptedException ignored) {}

                                                            batch.execute(); // Blocks until all requests callback

                                                            // Remove successful messages
                                                            Iterator<Message> iter = allMessages.iterator();
                                                            int index = 0;
                                                            while (iter.hasNext()) {
                                                                iter.next();
                                                                if (respMessages.get(index)) {
                                                                    iter.remove();
                                                                }
                                                                index++;
                                                            }
                                                            respMessages.clear();

                                                            if (allMessages.size() == 0) {
                                                                break;
                                                            }

                                                            System.out.println(allMessages.size());
                                                        }
                                                    }

                                                    delay = 500.0;
                                                    // System.out.println(local);
                                                } catch (MessagingException | IOException | ArithmeticException e) {
                                                    delay = handleBackoff(e, delay);
                                                }
                                        } catch (IOException | GeneralSecurityException e) {
                                            e.printStackTrace();
                                        }
                                    }).start();
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        break;
                    }
                    case "slowspam": {
                        if (cmd.length != 2) {
                            System.out
                                .println("Invalid number of arguments! There should be only one, specifying the email address to spam" +
                                    ".\n\t slowspam example@gmail.com\n\t slowspam stop");
                            break;
                        }

                        toEmail = cmd[1];

                        if (!toEmail.equals("stop")) {
                            AtomicInteger j = new AtomicInteger(0);

                            try {
                                for (String name : getUsernames()) {
                                    new Thread(() -> {
                                        try {
                                            NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
                                            Gmail gmail = new Gmail.Builder(HTTP_TRANSPORT, RSSFeedReader.JSON_FACTORY, RSSFeedReader
                                                .getCredentials(HTTP_TRANSPORT, name)).setApplicationName(RSSFeedReader.APPLICATION_NAME)
                                                .build();

                                            double delay = 500.0;
                                            int local = j.getAndIncrement();

                                            while (!toEmail.equals("stop"))
                                                try {
                                                    Message m = GoogleMail.createMessageWithEmail(GoogleMail
                                                        .createEmail(toEmail, "ignore@gmail.com", Long
                                                            .toHexString(Double.doubleToLongBits(Math.random() / Math.random())), String
                                                            .valueOf(local)));
                                                    local = j.getAndIncrement();

                                                    gmail.users().messages().send("me", m).execute();

                                                    delay = 500.0;
                                                    // System.out.println(local);
                                                } catch (MessagingException | IOException | ArithmeticException e) {
                                                    delay = handleBackoff(e, delay);
                                                }
                                        } catch (IOException | GeneralSecurityException e) {
                                            e.printStackTrace();
                                        }
                                    }).start();
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        break;
                    }
                    case "calcnum": {
                        // TODO: Add ability to pass in numbers and operation
                        if (cmd.length != 2) {
                            System.out
                                .println("Invalid number of arguments! There should be only one, specifying the email address to send " +
                                    "the result to.\n\t calcnum example@gmail.com");
                            break;
                        }

                        toEmail = cmd[1];

                        new Thread(() -> {
                            try {
                                NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
                                String user = Objects.requireNonNull(new File(RSSFeedReader.CREDENTIALS_FOLDER).listFiles())[2].getName();
                                Gmail gmail = new Gmail.Builder(HTTP_TRANSPORT, RSSFeedReader.JSON_FACTORY, RSSFeedReader
                                    .getCredentials(HTTP_TRANSPORT, user)).setApplicationName(RSSFeedReader.APPLICATION_NAME).build();
                                double startTime = System.nanoTime();

                                // Calculate 2 ^ (2 ^ 31)
                                BigInteger b = new BigInteger("1"), c = new BigInteger("2");
                                c = c.pow(16);

                                // Have to do repeated left shifts because 2 ^ 64 is outside the range of an int and BigInteger's only
                                // take ints as a parameter into pow()
                                for (BigInteger i = new BigInteger("0"); !i.equals(c); i = i.add(BigInteger.ONE)) {
                                    b = b.shiftLeft(32768);
                                }

                                String body = c.toString() + "\n" + (System.nanoTime() - startTime) / 1000000000.0;
                                GoogleMail.sendMessage(gmail, "me", GoogleMail.createEmail(toEmail, toEmail, "Huge calc is done", body));
                            } catch (IOException | GeneralSecurityException | MessagingException e) {
                                e.printStackTrace();
                            }
                        }).start();
                        break;
                    }
                }
            }
        }).start();
    }

    private static ArrayList<String> getUsernames() throws Exception {
        ArrayList<String> fileNames = Arrays.stream(Objects.requireNonNull(new File(RSSFeedReader.CREDENTIALS_FOLDER).listFiles()))
            .map(File::getName).collect(Collectors.toCollection(ArrayList::new));
        fileNames.removeAll(Files.readAllLines(Paths.get("ignore_emails.txt"), Charsets.UTF_8));
        return fileNames;
    }

    private static double handleBackoff(Exception e, double delay) {
        if (e instanceof GoogleJsonResponseException) {
            int code = ((GoogleJsonResponseException) e).getDetails().getCode();
            System.out.println("Error: " + code + " " + ((GoogleJsonResponseException) e).getDetails().getMessage());

            if (String.valueOf(code).startsWith("5") || code == 429) {
                try {
                    System.out.println("backoff " + Math.min(delay, 20000.0));
                    Thread.sleep((long) (Math.min(delay, 20000.0) + Math.random() * 500));
                    delay *= 2.0;
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
            }
        } else {
            e.printStackTrace();
        }

        return delay;
    }
}
