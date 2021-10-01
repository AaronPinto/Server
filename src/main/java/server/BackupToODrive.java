package server;

import com.microsoft.graph.models.Drive;
import com.microsoft.graph.models.User;

import java.io.IOException;
import java.time.LocalDate;

public class BackupToODrive {
    private static final String name = "aaron-" + LocalDate.now() + ".zip";
    private static final String storeZipLocation = "D:/" + name;
    private static final String[] roots = new String[]{System.getProperty("user.home"), "D:/"};

    private BackupToODrive() {
        // Prevent class from being instantiated
    }

    public static void main(String[] args) throws IOException {
        double start = System.nanoTime();

        // compressAndArchive(visitPaths(pathsToVisit(roots, "excludePaths.txt", "includePaths.txt")), storeZipLocation);

        System.out.println((System.nanoTime() - start) / 60000000000.0 + " min");
        System.out.println("Starting upload to OneDrive");

        // Upload to OneDrive
        // Initialize Graph with auth settings
        Graph.initializeGraphAuth();
        final String accessToken = Graph.getUserAccessToken();
        System.out.println("Access token: " + accessToken);

        // Get authorised graph client
        final var graphClient = Graph.getGraphClient();

        final Drive result = graphClient.me().drive().buildRequest().get();
        System.out.println("Found Drive " + (result != null ? result.id : null));

        // TODO: Upload file


        // Greet the user
        User user = Graph.getUser();
        System.out.println("Welcome " + user.displayName);

        System.out.println((System.nanoTime() - start) / 60000000000.0 + " min");
    }
}
