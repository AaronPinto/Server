package server;

import com.azure.identity.InteractiveBrowserCredential;
import com.azure.identity.InteractiveBrowserCredentialBuilder;
import com.microsoft.graph.authentication.TokenCredentialAuthProvider;
import com.microsoft.graph.logger.DefaultLogger;
import com.microsoft.graph.logger.LoggerLevel;
import com.microsoft.graph.models.User;
import com.microsoft.graph.requests.GraphServiceClient;
import okhttp3.Request;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public final class Graph {
    /**
     * Used for Microsoft Graph authentication
     */
    private static final String OAUTH_SECRET_DIR = "/oAuth.properties";
    private static GraphServiceClient<Request> graphClient = null;
    private static TokenCredentialAuthProvider authProvider = null;

    private Graph() {
        // Prevent class from being instantiated
    }

    public static void initializeGraphAuth() throws IOException {
        // Load OAuth settings
        final Properties oAuthProperties = new Properties();
        oAuthProperties.load(Server.class.getResourceAsStream(OAUTH_SECRET_DIR));

        final String appId = oAuthProperties.getProperty("app.id");
        final List<String> appScopes = Arrays.asList(oAuthProperties.getProperty("app.scopes").split(","));

        // Create the auth provider
        final InteractiveBrowserCredential credential = new InteractiveBrowserCredentialBuilder().clientId(appId)
                .redirectUrl("http://localhost:8765").tenantId("common").build();

        authProvider = new TokenCredentialAuthProvider(appScopes, credential);

        // Create default logger to only log errors
        DefaultLogger logger = new DefaultLogger();
        logger.setLoggingLevel(LoggerLevel.ERROR);

        // Build a Graph client
        graphClient = GraphServiceClient.builder().authenticationProvider(authProvider).logger(logger).buildClient();
    }

    public static GraphServiceClient<Request> getGraphClient() {
        if (graphClient == null) {
            throw new NullPointerException("Graph client has not been initialized. Call initializeGraphAuth before calling this method");
        }

        return graphClient;
    }

    public static String getUserAccessToken() {
        try {
            URL meUrl = new URL("https://graph.microsoft.com/v1.0/me");
            return authProvider.getAuthorizationTokenAsync(meUrl).get();
        } catch (Exception ex) {
            return null;
        }
    }

    public static User getUser() {
        if (graphClient == null) {
            throw new NullPointerException("Graph client has not been initialized. Call initializeGraphAuth before calling this method");
        }

        // GET /me to get authenticated user
        return graphClient.me().buildRequest().select("displayName").get();
    }
}
