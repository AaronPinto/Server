package server;

import com.microsoft.graph.models.DateTimeTimeZone;
import com.microsoft.graph.models.Drive;
import com.microsoft.graph.models.Event;
import com.microsoft.graph.models.User;

import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Objects;

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
        System.out.println("Found Drive " + result.id);

        // Greet the user
        User user = Graph.getUser();
        System.out.println("Welcome " + user.displayName);
        System.out.println("Time zone: " + Objects.requireNonNull(user.mailboxSettings).timeZone);
        System.out.println();

        // List the calendar
        listCalendarEvents(user.mailboxSettings.timeZone);

        System.out.println((System.nanoTime() - start) / 60000000000.0 + " min");
    }

    private static void listCalendarEvents(String timeZone) {
        ZoneId tzId = ZoneId.of("America/Toronto");

        // Get midnight of the first day of the week (assumed Sunday)
        // in the user's timezone, then convert to UTC
        ZonedDateTime startOfWeek = ZonedDateTime.now(tzId).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
                .truncatedTo(ChronoUnit.DAYS).withZoneSameInstant(ZoneId.of("UTC"));

        // Add 7 days to get the end of the week
        ZonedDateTime endOfWeek = startOfWeek.plusDays(7);

        // Get the user's events
        List<Event> events = Graph.getCalendarView(startOfWeek, endOfWeek, timeZone);

        System.out.println("Events:");

        for (Event event : events) {
            System.out.println("Subject: " + event.subject);
            if (event.organizer != null && event.organizer.emailAddress != null) {
                System.out.println("  Organizer: " + event.organizer.emailAddress.name);
            }
            System.out.println("  Start: " + formatDateTimeTimeZone(Objects.requireNonNull(event.start)));
            System.out.println("  End: " + formatDateTimeTimeZone(Objects.requireNonNull(event.end)));
        }

        System.out.println();
    }

    private static String formatDateTimeTimeZone(DateTimeTimeZone date) {
        LocalDateTime dateTime = LocalDateTime.parse(Objects.requireNonNull(date.dateTime));

        return dateTime.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)) + " (" + date.timeZone + ")";
    }
}
