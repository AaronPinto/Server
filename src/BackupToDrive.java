import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.services.drive.Drive;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.util.*;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class BackupToDrive {
	private static Object[] size(Path path) throws IOException {
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
				if(exc != null) System.out.println("had trouble traversing: " + dir + " (" + exc + ")");
				return FileVisitResult.CONTINUE;
			}
		});

		System.out.println(all.size() + " " + failed.size());
		return new Object[]{all, failed};
	}

	private static boolean dank(Path p, ArrayList<String> prevDirs) {
		String s = p.toString();

		for(String f : prevDirs)
			if(s.contains(f)) return true;

		//Truncate path to 4 directories excluding last slash
		int count = 0;
		for(int i = 0; i < s.length(); i++) {
			if(s.charAt(i) == '\\') count++;
			if(count == 4) {
				prevDirs.add(0, s = s.substring(0, i));
				System.out.println(s);
				break;
			}
		}
		return true;
	}

	private static String home = System.getProperty("user.home"), name = "aaron-" + LocalDate.now().toString();

	public static void main(String[] args) throws IOException, GeneralSecurityException {
		double start = System.nanoTime();
		Object[] dank = size(Paths.get(home));
		LinkedHashMap<Path, Boolean> all = (LinkedHashMap<Path, Boolean>) dank[0];
		ArrayList<String> failed = (ArrayList<String>) dank[1];

		//Truncate all failed strings to 5 directories excluding last slash
		for(int i = 0; i < failed.size(); i++) {
			String s = failed.get(i);
			if(s.contains("AppData")) {
				failed.set(i, s.substring(0, s.indexOf("AppData") + "AppData".length()));
				continue;
			}

			int count = 0;
			for(int j = 0; j < s.length(); j++) {
				if(s.charAt(j) == '\\') count++;
				if(count == 5) {
					failed.set(i, s.substring(0, j));
					break;
				}
			}
		}

		//Remove duplicates, sets can only contain distinct elements
		Set<String> hs = new LinkedHashSet<>(failed);
		failed = new ArrayList<>(hs);
		System.out.println(failed);

		//Remove files in updated failed directories from all files
		for(Iterator<Path> it = all.keySet().iterator(); it.hasNext(); ) {
			String path = it.next().toString();
			for(String s : failed)
				if(path.contains(s)) {
					it.remove();
					break;
				}
		}

		//Compress files and add to zip archive
		try(ZipOutputStream zs = new ZipOutputStream(Files.newOutputStream(Files.createFile(Paths.get("D:/" + name + ".zip"))))) {
			zs.setLevel(Deflater.BEST_COMPRESSION);
			ArrayList<String> prevDirs = new ArrayList<>(50);
			Path p = Paths.get(home);

			for(Path path : all.keySet())
				if(all.get(path) && dank(path, prevDirs)) {
					ZipEntry zipEntry = new ZipEntry(p.relativize(path).toString());
					try {
						zs.putNextEntry(zipEntry);
						Files.copy(path, zs);
						zs.closeEntry();
					} catch(IOException e) {
						e.printStackTrace();
					}
				}
		}

		System.out.println((System.nanoTime() - start) / 1000000000.0);

		//Upload to Google Drive
		NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
		Drive service = new Drive.Builder(HTTP_TRANSPORT, RSSFeedReader.JSON_FACTORY, RSSFeedReader.getCredentials(HTTP_TRANSPORT, "pintoa9"))
				.setApplicationName(RSSFeedReader.APPLICATION_NAME).build();

		var fileMetadata = new com.google.api.services.drive.model.File();
		fileMetadata.setName(name);
		File filePath = new File("D:/" + name + ".zip");
		FileContent mediaContent = new FileContent("", filePath);

		Drive.Files.Create request = service.files().create(fileMetadata, mediaContent).setFields("id");
		request.getMediaHttpUploader().setProgressListener(new CustomProgressListener());
		var file = request.execute();
		System.out.println((System.nanoTime() - start) / 1000000000.0);
		System.out.println("File ID: " + file.getId());
	}

	static class CustomProgressListener implements MediaHttpUploaderProgressListener {
		public void progressChanged(MediaHttpUploader uploader) throws IOException {
			switch(uploader.getUploadState()) {
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