package server;

import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import com.google.api.client.http.FileContent;
import com.google.api.services.drive.Drive;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;

import static server.util.BackupFiles.*;

public class BackupToGDrive {
    private static final String name = "aaron-" + LocalDate.now() + ".zip";
    private static final String storeZipLocation = "D:/" + name;
    private static final String[] roots = new String[]{System.getProperty("user.home"), "D:/"};

    private BackupToGDrive() {
        // Prevent class from being instantiated
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("There should be an arg specifying the username of the Google Drive account to upload the file to");
            throw new IllegalArgumentException("Not enough arguments!");
        }

        double start = System.nanoTime();

        compressAndArchive(visitPaths(pathsToVisit(roots, "excludePaths.txt", "includePaths.txt")), storeZipLocation);

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
