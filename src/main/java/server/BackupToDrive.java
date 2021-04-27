package server;

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
    private static final String name = "aaron-" + LocalDate.now() + ".zip";
    private static final String storeZipLocation = "D:/" + name;
    private static final String[] roots = new String[]{System.getProperty("user.home"), "D:/"};

    private BackupToDrive() {
        // Prevent class from being instantiated
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("There should be an arg specifying the username of the Google Drive account to upload the file to");
            throw new IllegalArgumentException("Not enough arguments!");
        }

        double start = System.nanoTime();

        compressAndArchive(visitPaths(pathsToVisit(roots, "excludePaths.txt", "includePaths.txt")));

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
    public static LinkedHashMap<String, ArrayList<Path>> pathsToVisit(String[] roots, String exc, String inc) throws IOException {
        Scanner s = new Scanner(System.in);
        LinkedHashMap<String, ArrayList<Path>> pathsPerRoot = new LinkedHashMap<>(roots.length);

        System.out.println("If prompted, input y or n to include the file/directory or not");
        if (exc != null && !exc.isBlank() && inc != null && !inc.isBlank()) {
            var exclude = Files.readAllLines(Paths.get(exc), Charsets.UTF_8);
            var include = Files.readAllLines(Paths.get(inc), Charsets.UTF_8);
            PrintWriter pw_exclude = new PrintWriter(new FileWriter(exc, true));
            PrintWriter pw_include = new PrintWriter(new FileWriter(inc, true));

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
                                pw_include.println(file);
                            } else {
                                pw_exclude.println(file.toString());
                            }
                        }
                    }
                }

                pathsPerRoot.put(root, paths);
            }

            pw_exclude.flush();
            pw_exclude.close();
            pw_include.flush();
            pw_include.close();
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
                // TODO: Replace this type with a record in Java 14+
                var temp = getFiles(path);
                all.putAll(temp.getAll());
                failed.addAll(temp.getFailed());
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

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(Files.createFile(Paths.get(storeZipLocation))))) {
            zos.setLevel(Deflater.BEST_COMPRESSION);
            String prevDir = "";

            for (var rootFiles : filesPerRoot.entrySet()) {
                Path root = Paths.get(rootFiles.getKey());
                int rootLen = root.toString().length();
                var all = rootFiles.getValue();

                for (Path path : all.keySet()) {
                    if (all.get(path)) {
                        String s = path.toString();

                        if (prevDir.equals("") || !s.contains(prevDir)) {
                            // Truncate path to 1 level below root excluding last slash
                            int nextSlash = s.indexOf(File.separatorChar, rootLen + 1);
                            prevDir = nextSlash > 0 ? s.substring(0, nextSlash) : s;
                            System.out.println(prevDir);
                        }

                        ZipEntry zipEntry = new ZipEntry(root.relativize(path).toString());

                        try {
                            zos.putNextEntry(zipEntry);
                            Files.copy(path, zos);
                            zos.closeEntry();
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
    private static FileVisitResults getFiles(Path path) throws IOException {
        var fvr = new FileVisitResults();

        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                fvr.all.put(file, !Files.isDirectory(file));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                fvr.failed.add(file.toString());
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

        return fvr;
    }

    private static class FileVisitResults {
        final LinkedHashMap<Path, Boolean> all = new LinkedHashMap<>();
        final ArrayList<String> failed = new ArrayList<>();

        public LinkedHashMap<Path, Boolean> getAll() {
            return all;
        }

        public ArrayList<String> getFailed() {
            return failed;
        }
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
