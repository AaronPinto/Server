package server;

import jakarta.mail.internet.AddressException;
import server.util.Spam;

import java.io.IOException;
import java.util.Arrays;
import java.util.Scanner;

/**
 * This class contains a command line interface that I created which contains some different commands that leverage the Gmail API
 */
public class CLI {
    private CLI() {
        // Prevent class from being instantiated
    }

    static void start() {
        new Thread(() -> {
            String[] cmd;
            Scanner s = new Scanner(System.in);
            while (true) {
                cmd = s.nextLine().split(" ");
                System.out.println("command is " + Arrays.toString(cmd));

                switch (cmd[0]) {
                    case "batchspam" -> {
                        if (cmd.length != 2 && cmd.length != 3) {
                            System.out.println("""
                                               Invalid number of arguments! There should be one specifying the email address to spam, and an optional one to suppress stack traces.
                                               \t batchspam example@gmail.com [--quiet]
                                               \t batchspam stop""");
                            break;
                        }

                        try {
                            Server.isValidEmailAddress(cmd[1]);
                        } catch (AddressException e) {
                            System.out.println("Invalid email address! Please make sure it's formatted correctly.");
                            break;
                        }

                        try {
                            Spam.batchSpam(cmd[1], cmd.length == 3);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    case "slowspam" -> {
                        if (cmd.length != 2 && cmd.length != 3) {
                            System.out.println("""
                                               Invalid number of arguments! There should be one specifying the email address to spam, and an optional one to suppress stack traces.
                                               \t slowspam example@gmail.com [--quiet]
                                               \t slowspam stop""");
                            break;
                        }

                        try {
                            Server.isValidEmailAddress(cmd[1]);
                        } catch (AddressException e) {
                            System.out.println("Invalid email address! Please make sure it's formatted correctly.");
                            break;
                        }

                        try {
                            Spam.slowSpam(cmd[1], cmd.length == 3);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    case "adduser" -> {
                        if (cmd.length != 2) {
                            System.out.println(
                                    "Invalid number of arguments! There should be only one, specifying the username of the email address " +
                                            "to add.\n\t adduser example");
                            break;
                        }

                        try {
                            Server.authorize(cmd[1]);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    case "help", default -> {
                        System.out.println("batchspam example@gmail.com\n\t batchspam stop");
                        System.out.println("slowspam example@gmail.com\n\t slowspam stop");
                        System.out.println("adduser example");
                    }
                }
            }
        }).start();
    }
}
