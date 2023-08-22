import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

public class runKleinanzeigen {

	public static void main(String[] args) throws IOException, InterruptedException {
		// TODO Auto-generated method stub
//		String anim = "|/-\\";
//		for (int x = 0; x < 100; x++) {
//			String data = "\r" + anim.charAt(x % anim.length());
//			System.out.write(data.getBytes());
//			Thread.sleep(100);
//		}
//
//		Thread.sleep(10000000);

		System.out.println("     ______               _   __  ___  \n" + "     | ___ \\             | | / / / _ \\ \n"
				+ "  ___| |_/ / __ _ _   _  | |/ / / /_\\ \\\n" + " / _ \\ ___ \\/ _` | | | | |    \\ |  _  |\n"
				+ "|  __/ |_/ / (_| | |_| | | |\\  \\| | | |\n" + " \\___\\____/ \\__,_|\\__, | \\_| \\_/\\_| |_/\n"
				+ "                   __/ |               \n" + "                  |___/                ");
		System.out.println("\nBy @VA#0001");

		Path path = Paths
				.get(System.getProperty("user.home") + "\\Desktop" + "\\Kleinanzeigen\\tasksKleinanzeigen.csv");
		Reader in = new FileReader(
				System.getProperty("user.home") + "\\Desktop" + "\\Kleinanzeigen\\tasksKleinanzeigen.csv");
		Iterable<CSVRecord> records = CSVFormat.EXCEL.withHeader().parse(in);

		System.out
				.println("\n\nInitializing " + Integer.valueOf((int) Files.lines(path).count() - 1) + " task(s)...\n");
		Thread.sleep(1000);

		for (CSVRecord record : records) {

			String url = record.get("url");
			long retryDelayMS = Long.valueOf(record.get("retryDelaySeconds")) * 1000;
			String useProxy = record.get("proxy [y/n]");
			String sendDM = record.get("sendDM [y/n]");
			String email = record.get("email");
			String password = record.get("password");
			String contactName = record.get("contactName");
			String twoCaptcha = record.get("2Captcha");
			String webhookUrl = record.get("webhookUrl");

			KleinanzeigenMonitorTask task = new KleinanzeigenMonitorTask(url, retryDelayMS, useProxy, sendDM, email,
					password, contactName, twoCaptcha, webhookUrl);
			task.start();

		}
	}

}
