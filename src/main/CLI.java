package main;

import main.util.Spam;

import java.io.IOException;
import java.util.Arrays;
import java.util.Scanner;

/**
 * This class contains a command line interface that I created which contains some different commands that leverage the Gmail API
 */
public class CLI {
    private static String[] cmd;

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
                                    .println("Invalid number of arguments! There should be only one, specifying the email address to " +
                                            "spam.\n\t batchspam example@gmail.com\n\t batchspam stop");
                            break;
                        }

                        try {
                            Spam.batchSpam(cmd[1]);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        break;
                    }
                    case "slowspam": {
                        if (cmd.length != 2) {
                            System.out
                                    .println("Invalid number of arguments! There should be only one, specifying the email address to " +
                                            "spam.\n\t slowspam example@gmail.com\n\t slowspam stop");
                            break;
                        }

                        try {
                            Spam.slowSpam(cmd[1]);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        break;
                    }
                    case "adduser": {
                        if (cmd.length != 2) {
                            System.out
                                    .println("Invalid number of arguments! There should be only one, specifying the username of the " +
                                            "email address to add.\n\t adduser example");
                            break;
                        }

                        try {
                            Server.authorize(cmd[1]);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        break;
                    }
                    case "help": {
                        System.out.println("batchspam example@gmail.com\n\t batchspam stop");
                        System.out.println("slowspam example@gmail.com\n\t slowspam stop");
                        System.out.println("adduser example");
                    }
                }
            }
        }).start();
    }

    public static void main(String[] args) {
        start();
    }
}
