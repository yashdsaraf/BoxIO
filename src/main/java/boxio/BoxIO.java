package boxio;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import com.box.sdk.BoxAPIException;
import com.box.sdk.BoxDeveloperEditionAPIConnection;
import com.box.sdk.BoxFile;
import com.box.sdk.BoxFolder;
import com.box.sdk.BoxItem;
import com.box.sdk.EncryptionAlgorithm;
import com.box.sdk.IAccessTokenCache;
import com.box.sdk.InMemoryLRUAccessTokenCache;
import com.box.sdk.JWTEncryptionPreferences;
import com.box.sdk.ProgressListener;

public class BoxIO {

	enum BoxIOEnum {
		LISTEN, UPLOAD
	}

	private static class BoxIOThread implements Callable<Boolean> {

		private int MAX_CACHE_ENTRIES = 100;
		private BoxIOEnum action;
		private List<String> files;
		private Map<String, String> details;
		private BoxDeveloperEditionAPIConnection api = null;
		private BoxFolder boxFolder;
		private short count;

		private BoxIOThread(String path) {
			File file = new File(path);
			try (FileInputStream propFile = new FileInputStream(file)) {
				if (!file.canRead())
					throw new IOException(path + " is not readable!");
				Properties props = new Properties();
				props.load(propFile);
				details = props.entrySet().stream()
						.collect(Collectors.toMap(e -> (String) e.getKey(), e -> (String) e.getValue()));
			} catch (IOException e) {
				System.err.println(e.getMessage());
			}
		}

		private boolean connectToBox() {
			String privateKey = new String(Base64.getDecoder().decode(details.get("private_key")));
			JWTEncryptionPreferences encryptionPref = new JWTEncryptionPreferences();
			encryptionPref.setPublicKeyID(details.get("public_key_id"));
			encryptionPref.setPrivateKey(privateKey);
			encryptionPref.setPrivateKeyPassword(details.get("private_key_password"));
			encryptionPref.setEncryptionAlgorithm(EncryptionAlgorithm.RSA_SHA_256);
			IAccessTokenCache accessTokenCache = new InMemoryLRUAccessTokenCache(MAX_CACHE_ENTRIES);
			api = BoxDeveloperEditionAPIConnection.getAppUserConnection(details.get("user_id"),
					details.get("client_id"), details.get("client_secret"), encryptionPref, accessTokenCache);
			boxFolder = new BoxFolder(api, details.get("folder_id"));
			return api != null;
		}

		private void setAction(BoxIOEnum action) {
			this.action = action;
		}

		private BoxIOEnum getAction() {
			return action;
		}

		private void setFiles(List<String> list) {
			files = list;
		}

		private void setCount(short count) {
			this.count = count;
		}

		@Override
		public Boolean call() {
			if (!connectToBox()) {
				System.out.println("Failed to connect with Box, RETRYING...");
				if (!connectToBox()) {
					System.err.println("Error occurred when connecting with Box!");
					return false;
				}
			}
			if (action == BoxIOEnum.LISTEN) {
				System.out.println("Started listening to " + boxFolder.getInfo().getName() + " folder...");
				boolean keepListening = true;
				short counter = 0;
				while (keepListening) {
					for (BoxItem.Info itemInfo : boxFolder) {
						BoxFile file = new BoxFile(api, itemInfo.getID());
						try (FileOutputStream stream = new FileOutputStream(itemInfo.getName())) {
							System.out.println("\n" + ++counter + ". Downloading file " + itemInfo.getName());
							ProgressBar bar = new ProgressBar();
							file.download(stream, new ProgressListener() {

								@Override
								public void onProgressChanged(long numBytes, long totalBytes) {
									int done = (int) numBytes / 1024;
									int total = (int) totalBytes / 1024;
									bar.update(done, total);
								}
							});
							file.delete();
						} catch (IOException e) {
							e.printStackTrace();
							return false;
						} catch (BoxAPIException e) {
							if (e.getResponseCode() == 404) {
								System.out.println("Network connection error....RETRYING");
								if (!connectToBox()) {
									System.err.println("Check your network connection");
									return false;
								}
							}
						}
					}
					if (counter == count)
						keepListening = false;
					try {
						Thread.sleep(2000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			} else {
				for (String filePath : files) {
					File file = new File(filePath);
					try (FileInputStream stream = new FileInputStream(file)) {
						if (!file.canRead()) {
							throw new IOException("Error occurred when reading " + filePath);
						}
						System.out.println("Uploading file " + filePath);
						BoxFile.Info info = boxFolder.uploadFile(stream, file.getName());
						if (info == null)
							return false;
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
			return true;
		}

	}

	public static void main(String... args) {
		if (args.length < 3) {
			System.out.println("Incorrect no. of arguements supplied!");
			System.exit(-1);
		}
		BoxIOThread box = new BoxIOThread(args[0]);
		box.setAction(BoxIOEnum.valueOf(args[1].toUpperCase()));
		if (box.getAction() == BoxIOEnum.UPLOAD) {
			List<String> fileList = new ArrayList<>();
			for (short i = 2; i < args.length; i++)
				fileList.add(args[i]);
			box.setFiles(fileList);
		} else {
			box.setCount(Short.parseShort(args[2]));
		}
		ExecutorService exService = Executors.newCachedThreadPool();
		Future<Boolean> future = exService.submit(box);
		try {
			if (!future.get()) {
				System.err.println("\nFailed!");
			} else {
				System.out.println("\nSuccess!");
			}
		} catch (ExecutionException | InterruptedException e) {
			e.printStackTrace(System.err);
		}
		System.exit(0);
	}
}