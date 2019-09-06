public class Server {
    // private static final String path = "C:/Users/aaron/Music";
    // private static final String path = "D:/Spotify Music";
    // private static File[] files = Objects.requireNonNull(new File(path).listFiles());

    public static void main(String[] args) {
        try {
            // JSONObject obj = new JSONObject();
            // JSONArray music = new JSONArray();
            //
            // for (File file : files)
            // 	if (file.getName().endsWith(".mp3") || file.getName().endsWith(".wav") || file.getName()
            // 		.endsWith(".ogg")) {
            // 		JSONObject song = new JSONObject();
            // 		song.put("title", file.getName().substring(0, file.getName().length() - 4));
            // 		song.put("source", file.getName());
            // 		song.put("duration", AudioFileIO.read(file).getAudioHeader().getPreciseTrackLength());
            //
            // 		music.put(song);
            // 	}
            //
            // obj.put("music", music);
            // try (PrintWriter out = new PrintWriter(path + "/music.json")) {
            // 	out.println(obj);
            // }

            RSSFeedReader.start();

            CLI.start();

            // BufferedReader in = new BufferedReader(new InputStreamReader(Server.class
            // 	.getResourceAsStream("Your Library - Songs.html")));
            // String line;
            // int counter = 0;
            // while ((line = in.readLine()) != null) {
            // 	int titleStartIndex = 0, titleEndIndex = 0;
            // 	while (titleStartIndex >= 0) {
            // 		titleStartIndex = line.indexOf("<span class=\"tracklist-name\">", titleEndIndex);
            // 		if (titleStartIndex >= 0) {
            // 			titleEndIndex = line.indexOf("</span>", titleStartIndex);
            // 			String name = line
            // 				.substring(titleStartIndex + "<span class=\"tracklist-name\">".length(), titleEndIndex);
            // 			name = name.replaceAll("&amp;", "&");
            // 			counter++;
            // 			System.out.println(name + " " + counter);
            // 		}
            // 	}
            // }
            // in.close();
            //
            // Robot robot = new Robot();
            // for (int i = 0; i < 200; i++) {
            // 	KeyboardSim.type("!aaron" + i + "\n", robot);
            // 	Thread.sleep(3000);
            // }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
