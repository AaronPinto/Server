import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.services.drive.Drive;
import com.google.common.base.Charsets;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.util.*;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class BackupToDrive {
    private static final String name = "aaron-" + LocalDate.now().toString();
    private static final String storeZipLocation = "D:/" + name + ".zip";

    public static void main(String[] args) throws IOException, GeneralSecurityException {
        double start = System.nanoTime();

        var filesPerRoot = visitPaths(pathsToVisit("excludePaths.txt", new String[]{System.getProperty("user.home"), "D:/"}));

        // // Truncate all failed strings to 5 directories excluding last slash, unless its anything to do with AppData
        // // in which case truncate to /AppData
        // for (int i = 0; i < failed.size(); i++) {
        // 	String s = failed.get(i);
        // 	if (s.contains("AppData")) {
        // 		failed.set(i, s.substring(0, s.indexOf("AppData") + "AppData".length()));
        // 		continue;
        // 	}
        //
        // 	int count = 0;
        // 	for (int j = 0; j < s.length(); j++) {
        // 		if (s.charAt(j) == '\\') { count++; }
        // 		if (count == 5) {
        // 			failed.set(i, s.substring(0, j));
        // 			break;
        // 		}
        // 	}
        // }
        //
        // // Remove duplicates, sets can only contain distinct elements
        // Set<String> hs = new LinkedHashSet<>(failed);
        // failed = new ArrayList<>(hs);
        // System.out.println(failed);
        //
        // // Remove files in updated failed directories from all files
        // for (Iterator<Path> it = all.keySet().iterator(); it.hasNext(); ) {
        // 	String path = it.next().toString();
        // 	for (String s : failed)
        // 		if (path.contains(s)) {
        // 			it.remove();
        // 			break;
        // 		}
        // }

        // Compress files and add to zip archive
        Files.deleteIfExists(Paths.get(storeZipLocation));
        try (ZipOutputStream zs = new ZipOutputStream(Files.newOutputStream(Files.createFile(Paths.get(storeZipLocation))))) {
            zs.setLevel(Deflater.BEST_COMPRESSION);
            ArrayList<String> prevDirs = new ArrayList<>(50);

            for (var rootFiles : filesPerRoot.entrySet()) {
                Path p = Paths.get(rootFiles.getKey());
                var all = rootFiles.getValue();

                for (Path path : all.keySet())
                    if (all.get(path) && checkPrevDirs(path, prevDirs)) {
                        ZipEntry zipEntry = new ZipEntry(p.relativize(path).toString());

                        try {
                            zs.putNextEntry(zipEntry);
                            Files.copy(path, zs);
                            zs.closeEntry();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
            }
        }

        System.out.println((System.nanoTime() - start) / 60000000000.0 + " min");
        System.out.println("Starting upload to Google Drive");

        // Upload to Google Drive
        NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        String user = Objects.requireNonNull(new File(RSSFeedReader.CREDENTIALS_FOLDER).listFiles())[1].getName();
        Drive service = new Drive.Builder(HTTP_TRANSPORT, RSSFeedReader.JSON_FACTORY, RSSFeedReader
        	.getCredentials(HTTP_TRANSPORT, user)).setApplicationName(RSSFeedReader.APPLICATION_NAME).build();
        System.out.println(service.getBaseUrl() + " " + service.getRootUrl() + " " + service.getServicePath());

        var fileMetadata = new com.google.api.services.drive.model.File().setName(name);
        File filePath = new File(storeZipLocation);
        FileContent mediaContent = new FileContent("", filePath);

        Drive.Files.Create request = service.files().create(fileMetadata, mediaContent).setFields("id");
        request.getMediaHttpUploader().setProgressListener(new ProgressListener());
        var file = request.execute();
        System.out.println((System.nanoTime() - start) / 60000000000.0 + " min");
        System.out.println("File ID: " + file.getId());
    }

    private static LinkedHashMap<String, ArrayList<Path>> pathsToVisit(String excludePathsLocation, String[] roots) throws IOException {
        final String[] exclude = Files.readAllLines(Paths.get(excludePathsLocation), Charsets.UTF_8).toArray(new String[0]);
        final PrintWriter pw = new PrintWriter(new FileWriter(excludePathsLocation, true));
        final Scanner s = new Scanner(System.in);
        final LinkedHashMap<String, ArrayList<Path>> pathsPerRoot = new LinkedHashMap<>(roots.length);

        System.out.println("Input y or n to include the file/directory or not");
        for (var root : roots) {
            final ArrayList<Path> paths = new ArrayList<>();

            for (File file : Objects.requireNonNull(new File(root).listFiles())) {
                System.out.println(file);

                if (Arrays.stream(exclude).noneMatch(name -> name.equals(file.toString()))) {
                    if (s.nextLine().equals("y")) {
                        paths.add(Paths.get(file.toURI()));
                    } else {
                        pw.println(file.toString());
                    }
                }
            }

            pathsPerRoot.put(root, paths);
        }
        pw.flush();
        pw.close();

        return pathsPerRoot;
    }

    private static LinkedHashMap<String, LinkedHashMap<Path, Boolean>> visitPaths(LinkedHashMap<String, ArrayList<Path>> pathsPerRoot) throws IOException {
        final LinkedHashMap<String, LinkedHashMap<Path, Boolean>> filesPerRoot = new LinkedHashMap<>(pathsPerRoot.size());
        final ArrayList<String> failed = new ArrayList<>();

        for (var rootPaths : pathsPerRoot.entrySet()) {
            final LinkedHashMap<Path, Boolean> all = new LinkedHashMap<>(100000);

            for (Path path : rootPaths.getValue()) {
                Object[] temp = getFiles(path);
                all.putAll((LinkedHashMap<Path, Boolean>) temp[0]);
                failed.addAll((ArrayList<String>) temp[1]);
            }

            filesPerRoot.put(rootPaths.getKey(), all);
        }
        System.out.println("These paths failed: " + failed);

        return filesPerRoot;
    }

    private static Object[] getFiles(Path path) throws IOException {
        LinkedHashMap<Path, Boolean> all = new LinkedHashMap<>(400000);
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
                if (exc != null) { System.out.println("had trouble traversing: " + dir + " (" + exc + ")"); }
                return FileVisitResult.CONTINUE;
            }
        });

        // System.out.println(all.size() + " " + failed.size());
        return new Object[]{all, failed};
    }

    private static boolean checkPrevDirs(Path p, ArrayList<String> prevDirs) {
        String s = p.toString();

        for (String f : prevDirs)
            if (s.contains(f)) { return true; }

        //Truncate path to 4 directories excluding last slash
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\\') { count++; }
            if (count == 4) {
                prevDirs.add(0, s = s.substring(0, i));
                System.out.println(s);
                break;
            }
        }
        return true;
    }

    static class ProgressListener implements MediaHttpUploaderProgressListener {
        public void progressChanged(MediaHttpUploader uploader) throws IOException {
            switch (uploader.getUploadState()) {
                case INITIATION_STARTED:
                    System.out.println("Initiation has started!");
                    break;
                case INITIATION_COMPLETE:
                    System.out.println("Initiation is complete!");
                    break;
                case MEDIA_IN_PROGRESS:
                    System.out.println("Progress: " + uploader.getProgress() * 100.0);
                    break;
                case MEDIA_COMPLETE:
                    System.out.println("Upload is complete!");
            }
        }
    }
}
