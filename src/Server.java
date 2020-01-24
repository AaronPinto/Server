public class Server {
    public static void main(String[] args) {
        try {
            RSSFeedReader.start();
            CLI.start();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
