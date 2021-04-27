import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import server.BackupToDrive;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

public class BackupTest {
    private static final InputStream systemIn = System.in;
    private static final PrintStream systemOut = System.out;

    private static ByteArrayInputStream testIn;
    private static ByteArrayOutputStream testOut;

    private void provideInput(String data) {
        testIn = new ByteArrayInputStream(data.getBytes());
        System.setIn(testIn);
    }

    @BeforeEach
    public void setUpOutput() {
        testOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(testOut));
    }

    @AfterEach
    public void restoreSystemInputOutput() {
        System.setIn(systemIn);
        System.setOut(systemOut);
    }

    @Test
    @DisplayName("Test invalid args length")
    public void invalidArgsLen() {
        var exception = assertThrows(IllegalArgumentException.class, () -> BackupToDrive.main(new String[0]));
        assertEquals("Not enough arguments!", exception.getMessage());
    }

    @Test
    public void sample() {
        provideInput("n\nn\n");

        // BackupToDrive.pathsToVisit(new String[]{"D:/1P13 Marking"}, "excludePaths.txt", "includePaths.txt");

        assertTrue(true, "false");
    }
}
