import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.ssl.SslConfigurationFactory;
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory;
import org.jaudiotagger.audio.AudioFileIO;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

public class Server {
	private static final String path = "C:/Users/aaron/Music";
	//	private static final String path = "D:/Spotify Music";
	private static File[] files = Objects.requireNonNull(new File(path).listFiles());
	private static ArrayList<String> fileNames = Arrays.stream(files).map(File::getName).collect(Collectors.toCollection(ArrayList::new));

	public static void main(String[] args) {
		try {
			HttpServer httpServer = HttpServer.create(new InetSocketAddress(80), 0);

			JSONObject obj = new JSONObject();
			JSONArray music = new JSONArray();

			for(File file : files) {
				if(file.getName().endsWith(".mp3") || file.getName().endsWith(".wav") || file.getName().endsWith(".ogg")) {
					JSONObject song = new JSONObject();
					song.put("title", file.getName().substring(0, file.getName().length() - 4));
					song.put("source", file.getName());
					song.put("duration", AudioFileIO.read(file).getAudioHeader().getPreciseTrackLength());

					music.put(song);
				}
			}

			obj.put("music", music);
			try(PrintWriter out = new PrintWriter(path + "/music.json")) {
				out.println(obj);
			}

			fileNames.forEach(fileName -> httpServer.createContext("/" + fileName, new MyHandler()));

			httpServer.setExecutor(null);
			httpServer.start();

			FtpServerFactory serverFactory = new FtpServerFactory();
			ListenerFactory factory = new ListenerFactory();

			SslConfigurationFactory ssl = new SslConfigurationFactory();
			ssl.setKeystoreFile(new File("ftpserver.jks"));
			ssl.setKeystorePassword("password");
			factory.setSslConfiguration(ssl.createSslConfiguration());
			factory.setImplicitSsl(false);

			serverFactory.addListener("default", factory.createListener());

			PropertiesUserManagerFactory userManagerFactory = new PropertiesUserManagerFactory();
			userManagerFactory.setFile(new File("users.properties"));
			serverFactory.setUserManager(userManagerFactory.createUserManager());

			serverFactory.createServer().start();

			RSSFeedReader.start();

			CLI.start();

//			BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(
//					new File("C:\\Users\\aaron\\workspace\\Server\\src\\Your Library - Songs.html"))));
//			String line;
//			int counter = 0;
//			while((line = in.readLine()) != null) {
//				int titleStartIndex = 0, titleEndIndex = 0;
//				while(titleStartIndex >= 0) {
//					titleStartIndex = line.indexOf("<span class=\"tracklist-name\">", titleEndIndex);
//					if(titleStartIndex >= 0) {
//						titleEndIndex = line.indexOf("</span>", titleStartIndex);
//						String name = line.substring(titleStartIndex + "<span class=\"tracklist-name\">".length(), titleEndIndex);
//						name = name.replaceAll("&amp;", "&");
//						counter++;
//						System.out.println(name + " " + counter);
//					}
//				}
//			}
//			in.close();

		} catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	static class MyHandler implements HttpHandler {
//		String prevRemoteAddress = "", prevSongPath = "";

		@Override
		public void handle(HttpExchange t) throws IOException {
			String requestAddress = "http://" + t.getRequestHeaders().getFirst("Host");
			System.out.println("got a request from " + t.getRemoteAddress() + " context " + t.getHttpContext().getPath() + " " + requestAddress);
			byte[] response = readFile(path + t.getHttpContext().getPath());
//			if((!t.getHttpContext().getPath().equals(prevSongPath) || !t.getRemoteAddress().toString().equals(prevRemoteAddress))
//					&& !t.getHttpContext().getPath().endsWith(".json")) {
//				String s = "<html>\n" +
//						"<body>\n" +
//						"<audio controls autoplay>\n" +
//						"<source src=\"" + requestAddress + t.getHttpContext().getPath() + "\" type=\"audio/mpeg\">\n" +
//						"</audio>\n" +
//						"</body>\n" +
//						"</html>";
//				response = s.getBytes();
//				prevSongPath = t.getHttpContext().getPath();
//				prevRemoteAddress = t.getRemoteAddress().toString();
//			} else response = readFile(path + t.getHttpContext().getPath());
			t.sendResponseHeaders(200, response.length);
			OutputStream os = t.getResponseBody();
			os.write(response);
			os.close();
		}
	}

	private static byte[] readFile(String path) throws IOException {
		return Files.readAllBytes(Paths.get(path));
	}
}