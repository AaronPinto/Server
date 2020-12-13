package main;

import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import com.google.api.client.http.FileContent;
import com.google.api.services.drive.Drive;
import com.google.common.base.Charsets;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Scanner;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class BackupToDrive {
    private static final String name = "aaron-" + LocalDate.now().toString() + ".zip";
    private static final String storeZipLocation = "D:/" + name;

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.out.println("There should be an arg specifying the username of the Google Drive account to upload the file to");
            System.exit(1);
        }

        double start = System.nanoTime();

        compressAndArchive(visitPaths(pathsToVisit(new String[]{System.getProperty("user.home"), "D:/"}, "excludePaths.txt",
                "includePaths.txt")));

        System.out.println((System.nanoTime() - start) / 60000000000.0 + " min");
        System.out.println("Starting upload to Google Drive");

        // Upload to Google Drive
        String user = args[0];
        Drive service = new Drive.Builder(Server.HTTP_TRANSPORT, Server.JSON_FACTORY, Server.authorize(user))
                .setApplicationName(Server.APPLICATION_NAME).build();

        var fileMetadata = new com.google.api.services.drive.model.File().setName(name);
        File filePath = new File(storeZipLocation);
        FileContent mediaContent = new FileContent("", filePath);

        Drive.Files.Create request = service.files().create(fileMetadata, mediaContent).setFields("id");
        request.getMediaHttpUploader().setProgressListener(new ProgressListener());
        var file = request.execute();
        System.out.println((System.nanoTime() - start) / 60000000000.0 + " min");
        System.out.println("File ID: " + file.getId());
    }

    /**
     * @param roots root directories to start accessing
     * @param exc   path to the txt file containing new-line separated paths to exclude
     * @param inc   path to the txt file containing new-line separated paths to include
     *
     * @return a LinkedHashMap with each root as a key and all of its sub-paths as a value
     *
     * @throws IOException from {@link Files#readAllLines(Path, Charset)} or {@link FileWriter#FileWriter(String, boolean)}
     */
    private static LinkedHashMap<String, ArrayList<Path>> pathsToVisit(String[] roots, String exc, String inc) throws IOException {
        Scanner s = new Scanner(System.in);
        LinkedHashMap<String, ArrayList<Path>> pathsPerRoot = new LinkedHashMap<>(roots.length);

        System.out.println("If prompted, input y or n to include the file/directory or not");
        if (exc != null && !exc.isBlank() && inc != null && !inc.isBlank()) {
            var exclude = Files.readAllLines(Paths.get(exc), Charsets.UTF_8);
            var include = Files.readAllLines(Paths.get(inc), Charsets.UTF_8);
            PrintWriter pwexclude = new PrintWriter(new FileWriter(exc, true));
            PrintWriter pwinclude = new PrintWriter(new FileWriter(inc, true));

            for (var root : roots) {
                ArrayList<Path> paths = new ArrayList<>();

                for (File file : Objects.requireNonNull(new File(root).listFiles())) {
                    if (exclude.stream().noneMatch(name -> name.equals(file.toString()))) {
                        if (include.stream().anyMatch(name -> name.equals(file.toString()))) {
                            paths.add(Paths.get(file.toURI()));
                        } else {
                            System.out.print("\r" + file + " ");

                            if (s.nextLine().equals("y")) {
                                paths.add(Paths.get(file.toURI()));
                                pwinclude.println(file.toString());
                            } else {
                                pwexclude.println(file.toString());
                            }
                        }
                    }
                }

                pathsPerRoot.put(root, paths);
            }

            pwexclude.flush();
            pwexclude.close();
            pwinclude.flush();
            pwinclude.close();
        } else {
            for (var root : roots) {
                ArrayList<Path> paths = new ArrayList<>();

                for (File file : Objects.requireNonNull(new File(root).listFiles())) {
                    System.out.println(file);

                    if (s.nextLine().equals("y")) {
                        paths.add(Paths.get(file.toURI()));
                    }
                }

                pathsPerRoot.put(root, paths);
            }
        }

        System.out.println("Got all paths");
        return pathsPerRoot;
    }

    /**
     * @param pathsPerRoot the result from {@link #pathsToVisit(String[], String, String)}}
     *
     * @return a LinkedHashMap with each root as a key and all of its valid sub-paths and if they're not a directory as a value
     *
     * @throws IOException from {@link #getFiles(Path)}
     */
    private static LinkedHashMap<String, LinkedHashMap<Path, Boolean>> visitPaths(LinkedHashMap<String, ArrayList<Path>> pathsPerRoot) throws IOException {
        LinkedHashMap<String, LinkedHashMap<Path, Boolean>> filesPerRoot = new LinkedHashMap<>(pathsPerRoot.size());
        ArrayList<String> failed = new ArrayList<>();

        for (var rootPaths : pathsPerRoot.entrySet()) {
            LinkedHashMap<Path, Boolean> all = new LinkedHashMap<>(100000);

            for (Path path : rootPaths.getValue()) {
                // TODO: Replace this Object[] with a record in Java 14+
                Object[] temp = getFiles(path);
                all.putAll((LinkedHashMap<Path, Boolean>) temp[0]);
                failed.addAll((ArrayList<String>) temp[1]);
            }

            filesPerRoot.put(rootPaths.getKey(), all);
        }

        System.out.println("These paths failed: " + failed);
        return filesPerRoot;
    }

    /**
     * @param filesPerRoot the result from {@link #visitPaths(LinkedHashMap)}}
     *
     * @throws IOException from {@link Files#deleteIfExists(Path)} or {@link Files#createFile(Path, FileAttribute[])}
     */
    private static void compressAndArchive(LinkedHashMap<String, LinkedHashMap<Path, Boolean>> filesPerRoot) throws IOException {
        Files.deleteIfExists(Paths.get(storeZipLocation));

        try (ZipOutputStream zs = new ZipOutputStream(Files.newOutputStream(Files.createFile(Paths.get(storeZipLocation))))) {
            zs.setLevel(Deflater.BEST_COMPRESSION);
            ArrayList<String> prevDirs = new ArrayList<>(50);

            for (var rootFiles : filesPerRoot.entrySet()) {
                Path p = Paths.get(rootFiles.getKey());
                var all = rootFiles.getValue();

                for (Path path : all.keySet()) {
                    if (all.get(path) && checkPrevDirs(path, prevDirs)) {
                        ZipEntry zipEntry = new ZipEntry(p.relativize(path).toString());

                        try {
                            zs.putNextEntry(zipEntry);
                            Files.copy(path, zs);
                            zs.closeEntry();
                        } catch (IOException e) {
                            System.err.println(path + " failed!");
                            // e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    /**
     * @param path path to file/directory tree to be walked
     *
     * @return an Object[] containing a LinkedHashMap of successful visits in index 0 and an ArrayList of failed visits in index 1
     *
     * @throws IOException from {@link Files#walkFileTree(Path, FileVisitor)}
     */
    private static Object[] getFiles(Path path) throws IOException {
        LinkedHashMap<Path, Boolean> all = new LinkedHashMap<>();
        ArrayList<String> failed = new ArrayList<>();

        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                all.put(file, !Files.isDirectory(file));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                failed.add(file.toString());
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                if (exc != null) {
                    System.out.println("had trouble traversing: " + dir + " (" + exc + ")");
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return new Object[]{all, failed};
    }

    private static boolean checkPrevDirs(Path p, ArrayList<String> prevDirs) {
        String s = p.toString();

        for (String f : prevDirs) {
            if (s.contains(f)) {
                return true;
            }
        }

        // Truncate path to 4 directories excluding last slash
        int count = 0;

        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\\') {
                count++;
            }
            if (count == 4) {
                prevDirs.add(0, s = s.substring(0, i));
                System.out.println(s);
                break;
            }
        }

        return true;
    }

    private static class ProgressListener implements MediaHttpUploaderProgressListener {
        @Override
        public void progressChanged(MediaHttpUploader uploader) throws IOException {
            switch (uploader.getUploadState()) {
                case INITIATION_STARTED:
                    System.out.println("Initiation has started!");
                    break;
                case INITIATION_COMPLETE:
                    System.out.println("Initiation is complete!");
                    break;
                case MEDIA_IN_PROGRESS:
                    // https://stackoverflow.com/a/7939820/6713362
                    System.out.printf("Progress: %.5f%%\r", uploader.getProgress() * 100.0);
                    break;
                case MEDIA_COMPLETE:
                    System.out.println("Upload is complete!");
                    break;
            }
        }
    }
}
