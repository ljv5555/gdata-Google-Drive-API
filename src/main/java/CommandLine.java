import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.java6.auth.oauth2.FileCredentialStore;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.media.MediaHttpDownloader;
import com.google.api.client.googleapis.media.MediaHttpDownloaderProgressListener;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.Drive.Changes;
import com.google.api.services.drive.Drive.Files;
import com.google.api.services.drive.Drive.Files.Get;
import com.google.api.services.drive.Drive.Revisions;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.Change;
import com.google.api.services.drive.model.ChangeList;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.Revision;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CommandLine {

	private static String CLIENT_ID = "974272400485.apps.googleusercontent.com";
	private static String CLIENT_SECRET = "HUZk4uFBqoDGr9V11No2ce9K";

	/**
	 * Be sure to specify the name of your application. If the application name
	 * is {@code null} or blank, the application will log a warning. Suggested
	 * format is "MyCompany-ProductName/1.0".
	 */
	private static final String APPLICATION_NAME = "DschosDriveClient/1.0.0";

	/** Authorizes the installed application to access user's protected data. */
	private static Credential authorize(final JsonFactory jsonFactory,
			final HttpTransport httpTransport) throws Exception {
		final FileCredentialStore credentialStore = new FileCredentialStore(
				new java.io.File(System.getProperty("user.home"),
						".credentials/drive.json"), jsonFactory);
		final GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
				httpTransport, jsonFactory, CLIENT_ID, CLIENT_SECRET,
				Collections.singleton(DriveScopes.DRIVE)).setCredentialStore(
				credentialStore).build();
		return new AuthorizationCodeInstalledApp(flow,
				new LocalServerReceiver()).authorize("user");
	}

	public static void main(final String[] args) {
		boolean force = false;

		int i;
		for (i = 0; i < args.length && args[i].startsWith("-"); i++) {
			final String arg = args[i];
			if (arg.equals("-f") || arg.equals("--force")) {
				force = true;
			} else {
				System.err.println("Unknown flag: " + arg);
				System.exit(1);
			}
		}

		if (args.length != i + 1) {
			System.err.println("Usage: " + CommandLine.class + " [-f] <id>");
			System.exit(1);
		}
		final String gdocId = args[i];

		final HttpTransport httpTransport = new NetHttpTransport();

		final JsonFactory jsonFactory = new JacksonFactory();
		try {
			final Credential credential = authorize(jsonFactory, httpTransport);
			final Drive drive = new Drive.Builder(httpTransport, jsonFactory,
					credential).setApplicationName(APPLICATION_NAME).build();

			download(drive, gdocId, force);
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}

	protected static void download(final Drive drive, final String gdocId,
			final boolean forceOverwrite) throws Exception {
		final Files files = drive.files();
		final Get get = files.get(gdocId);
		final File file2 = get.execute();
		final String url = file2.getExportLinks().get(
				"application/vnd.oasis.opendocument.text");

		final String fileName = file2.getTitle().replace(" ", "") + ".odt";
		final java.io.File file = new java.io.File(fileName);
		if (!forceOverwrite && file.exists()) {
			System.err.println("File " + file + " already exists; skipping");
			System.exit(1);
		}
		final OutputStream out = new FileOutputStream(file);
		final MediaHttpDownloader downloader = get.getMediaHttpDownloader();
		downloader.setProgressListener(getProgressListener());
		downloader.download(new GenericUrl(url), out);
		out.close();
		System.err.println("\rDownloaded " + file.getAbsolutePath());
	}

	protected static MediaHttpDownloaderProgressListener getProgressListener() {
		return new MediaHttpDownloaderProgressListener() {

			public void progressChanged(final MediaHttpDownloader downloader2)
					throws IOException {
				System.err.print("\r" + downloader2.getProgress()
						+ "...           ");
			}

		};
	}

	protected static void showRevisions(final Drive drive, final String gdocId)
			throws Exception {
		final Revisions revisions = drive.revisions();
		for (final Revision revision : revisions.list(gdocId).execute()
				.getItems()) {
			System.err.println("revision: " + revision);
		}
	}

	protected static void showChanges(final Drive drive) throws Exception {
		// run commands
		final List<Change> result = new ArrayList<Change>();
		final Changes.List request = drive.changes().list();
		request.setStartChangeId(1343l);

		do {
			try {
				final ChangeList changes = request.execute();

				result.addAll(changes.getItems());
				request.setPageToken(changes.getNextPageToken());
			} catch (final IOException e) {
				System.out.println("An error occurred: " + e);
				request.setPageToken(null);
			}
		} while (request.getPageToken() != null
				&& request.getPageToken().length() > 0);

		for (final Change change : result) {
			final File file = change.getFile();
			System.err.println("change: "
					+ change.get("createdDate")
					+ " "
					+ (file == null ? "(nobody)" : file
							.getLastModifyingUserName()) + " modified "
					+ (file == null ? "(null)" : file.getTitle()));
		}
	}
}
