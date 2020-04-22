package main.util;

import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import main.Server;

import javax.mail.MessagingException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class Spam {
    private static volatile String toEmail = "";
    private static volatile boolean batchRunning = false, slowRunning = false; // You can either batch spam or slow spam someone
    private static final ArrayList<Thread> mailThreads = new ArrayList<>();

    private Spam() {
        // Prevent class from being initialized
    }

    public static void batchSpam(String email) throws IOException, SecurityException, NullPointerException {
        toEmail = email;

        if (toEmail.equals("stop")) {
            batchRunning = false;
            stopMailThreads();
            return;
        }
        if (batchRunning) {
            return;
        }

        mailThreads.clear();
        slowRunning = false;
        batchRunning = true;
        AtomicInteger j = new AtomicInteger(0);

        for (String name : Server.getUsernames()) {
            Thread t = new Thread(() -> {
                Gmail gmail = initGmail(name);

                if (gmail == null) {
                    return;
                }

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

                while (!toEmail.equals("stop") && !slowRunning) {
                    try {
                        // Generate a random subject so Gmail doesn't group emails in the inbox
                        allMessages.add(GoogleMail.createMessageWithEmail(GoogleMail.createEmail(toEmail, "ignore@gmail.com", Long
                                .toHexString(Double.doubleToLongBits(ThreadLocalRandom.current().nextDouble())), String.valueOf(local))));
                        local = j.getAndIncrement();

                        if (allMessages.size() >= 10) {
                            while (allMessages.size() > 0) {
                                for (Message m : allMessages) {
                                    gmail.users().messages().send("me", m).queue(batch, callback);
                                }

                                numReqSent += batch.size();

                                try {
                                    long ts = (long) Math
                                            .max(0, (numReqSent / reqPerSec - (System.currentTimeMillis() - start) / 1000.0) * 1000.0);
                                    System.out.println("currReqPerSec: " + numReqSent / ((System
                                            .currentTimeMillis() - start) / 1000.0) + " " + "sleepFor: " + ts);
                                    Thread.sleep(ts);
                                    System.out.println("currReqPerSec: " + numReqSent / ((System.currentTimeMillis() - start) / 1000.0));
                                } catch (InterruptedException ignored) {
                                }

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
                    } catch (MessagingException | IOException e) {
                        delay = handleBackoff(e, delay);
                    }
                }
            });

            t.start();
            mailThreads.add(t);
        }
    }

    public static void slowSpam(String email) throws IOException, SecurityException, NullPointerException {
        toEmail = email;

        if (toEmail.equals("stop")) {
            slowRunning = false;
            stopMailThreads();
            return;
        }
        if (slowRunning) {
            return;
        }

        mailThreads.clear();
        batchRunning = false;
        slowRunning = true;
        AtomicInteger j = new AtomicInteger(0);

        for (String name : Server.getUsernames()) {
            Thread t = new Thread(() -> {
                Gmail gmail = initGmail(name);

                if (gmail == null) {
                    return;
                }

                double delay = 500.0;
                int local = j.getAndIncrement();

                while (!toEmail.equals("stop") && !batchRunning) {
                    try {
                        Message m = GoogleMail.createMessageWithEmail(GoogleMail.createEmail(toEmail, "ignore@gmail.com", Long
                                .toHexString(Double.doubleToLongBits(ThreadLocalRandom.current().nextDouble())), String.valueOf(local)));
                        local = j.getAndIncrement();

                        gmail.users().messages().send("me", m).execute();

                        delay = 500.0;
                        // System.out.println(local);
                    } catch (MessagingException | IOException e) {
                        delay = handleBackoff(e, delay);
                    }
                }
            });

            t.start();
            mailThreads.add(t);
        }
    }

    private static void stopMailThreads() {
        System.out.println("Stopping threads...");

        for (Thread t : mailThreads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        mailThreads.clear();
        System.out.println("Threads stopped!");
    }

    private static Gmail initGmail(String name) {
        NetHttpTransport HTTP_TRANSPORT;
        Gmail gmail;

        try {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            gmail = new Gmail.Builder(HTTP_TRANSPORT, Server.JSON_FACTORY, Server.getCredentials(HTTP_TRANSPORT, name))
                    .setApplicationName(Server.APPLICATION_NAME).build();
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            return null;
        }

        return gmail;
    }

    private static double handleBackoff(Exception e, double delay) {
        if (e instanceof GoogleJsonResponseException) {
            int code = ((GoogleJsonResponseException) e).getDetails().getCode();
            System.out.println("Error: " + code + " " + ((GoogleJsonResponseException) e).getDetails().getMessage());

            if (String.valueOf(code).startsWith("5") || code == 429) {
                try {
                    System.out.println("backoff " + Math.min(delay, 20000.0));
                    Thread.sleep((long) (Math.min(delay, 20000.0) + ThreadLocalRandom.current().nextDouble() * 500));
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
