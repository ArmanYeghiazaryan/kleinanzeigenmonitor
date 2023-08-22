import java.awt.Color;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Proxy;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.sound.sampled.LineUnavailableException;

import org.apache.commons.text.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.google.common.base.Stopwatch;
import com.twocaptcha.TwoCaptcha;
import com.twocaptcha.captcha.ReCaptcha;

import net.dongliu.requests.BasicAuth;
import net.dongliu.requests.Proxies;
import net.dongliu.requests.RawResponse;
import net.dongliu.requests.Requests;
import net.dongliu.requests.Session;

//String Modes: Preload, Normal, Register
public class KleinanzeigenMonitorTask extends Thread {

	private HashSet<String> itemSet = new HashSet<String>();

	private String ip;
	private int port;

	private long retryDelayMS;
	private String url;
	private String email;
	private String password;
	private String contactName;
	private String twoCaptcha;
	private boolean useProxy = false;
	private boolean sendDM = false;

	private String webhookUrl;

	private static DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
	private static LocalDateTime now = LocalDateTime.now();
	private LoginTask loginTask;

	public KleinanzeigenMonitorTask(String url, long retryDelayMS, String useProxy, String sendDM, String email,
			String password, String contactName, String twoCaptcha, String webhookUrl) {

		this.url = url;
		this.retryDelayMS = retryDelayMS;

		if (useProxy.equals("y")) {
			this.useProxy = true;
		}

		if (sendDM.equals("y")) {
			this.sendDM = true;
			this.email = email;
			this.password = password;
			this.contactName = URLEncoder.encode(contactName, StandardCharsets.UTF_8);
			this.twoCaptcha = twoCaptcha;

		}

		this.webhookUrl = webhookUrl;

	}

	public void run() {
		System.out.println("[EKA] - [" + dtf.format(now.now()) + "] - [" + url + "] - Starting Task...");

		if (this.sendDM) {
			loginTask = new LoginTask(email, password, twoCaptcha);
			loginTask.start();
		}

		boolean firstIteration = true;
		while (true) {

			try {

				if (this.sendDM) {
					while (loginTask.getCookiesList() == null) {
						Thread.sleep(10000);
					}
				}

				monitorProducts(true);

			} catch (Exception e) {
				try {
					sendFailedWebhook(e.toString());
				} catch (Exception b) {
					// TODO Auto-generated catch block
				}
				System.out.println(
						"[EKA] - [" + dtf.format(now.now()) + "] - [" + url + "] - Loop Error: " + e.toString());
			}
		}

	}

	public boolean sendMessage(String adID) throws Exception {
		Session session = Requests.session();

		System.out.println("[EKA] - [" + dtf.format(now.now()) + "] - [" + url + "] - Sending Message...");
		Map<String, Object> request = new HashMap<>();
		request.put("Authority", "www.ebay-kleinanzeigen.de");
		request.put("sec-ch-ua", "\" Not A;Brand\";v=\"99\", \"Chromium\";v=\"98\", \"Google Chrome\";v=\"98\"");
		request.put("X-Csrf-Token", loginTask.getCsrfToken());
		request.put("Sec-Ch-Ua-Mobile", "?0");
		request.put("User-Agent",
				"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/98.0.4758.82 Safari/537.36");
		request.put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
		request.put("Accept", "*/*");
		request.put("X-Requested-With", "XMLHttpRequest");
		request.put("Sec-Ch-Ua-Platform", "\"Windows\"");
		request.put("Origin", "https://www.ebay-kleinanzeigen.de");
		request.put("Sec-Fetch-Site", "same-origin");
		request.put("Sec-Fetch-Mode", "cors");
		request.put("Sec-Fetch-Dest", "empty");
		request.put("Referer", "https://www.ebay-kleinanzeigen.de/s-anzeige/nike-air-max/1994754847-159-7729");
		request.put("Accept-Language", "de,en-GB;q=0.9,en;q=0.8,en-US;q=0.7,es;q=0.6,ca;q=0.5");

		String body = "message=" + getMessage() + "&adId=" + adID + "&adType=private&contactName=" + contactName
				+ "&phoneNumber=";

		Map<String, Object> cookies = new HashMap<>();

		for (org.openqa.selenium.Cookie cookie : loginTask.getCookiesList()) {
			cookies.put(cookie.getName(), cookie.getValue());
		}

		RawResponse newSession = null;
		if (useProxy) {
			setProxy();
			newSession = session.post("https://www.ebay-kleinanzeigen.de/s-anbieter-kontaktieren.json").headers(request)
					.body(body).cookies(cookies).socksTimeout(60_000).connectTimeout(60_000)
					.proxy(Proxies.httpProxy(ip, port)).send();
		} else {
			newSession = session.post("https://www.ebay-kleinanzeigen.de/s-anbieter-kontaktieren.json").headers(request)
					.body(body).cookies(cookies).socksTimeout(60_000).connectTimeout(60_000).send();
		}

		String response = newSession.readToText();

		if (response.contains("\"message\":\"Nachricht gesendet!\"")) {
			System.out.println(
					"[EKA] - [" + dtf.format(now.now()) + "] - [" + url + "] - SENT MESSAGE SUCCEEDED: " + adID);
			return true;
		} else {
			write(response);
			System.out
					.println("[EKA] - [" + dtf.format(now.now()) + "] - [" + url + "] - SENT MESSAGE FAILED: " + adID);
			return false;
		}
	}

	public void monitorProducts(boolean notify) throws InterruptedException, IOException, LineUnavailableException {
		setProxy();

		Session session = Requests.session();

		System.out.println("[EKA] - [" + dtf.format(now.now()) + "] - [" + url + "] - Monitoring Products...");
		Map<String, Object> request = new HashMap<>();
		request.put("Authority", "https://www.ebay-kleinanzeigen.de/");
		request.put("Cache-Control", "max-age=0");
		request.put("Sec-Ch-Ua", "\" Not;A Brand\";v=\"99\", \"Google Chrome\";v=\"97\", \"Chromium\";v=\"97\"");
		request.put("Sec-Ch-Ua-Mobile", "?0");
		request.put("Sec-Ch-Ua-Platform", "\"Windows\"");
		request.put("Upgrade-Insecure-Requests", "1");
		request.put("User-Agent",
				"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.60 Safari/537.36");
		request.put("Accept",
				"text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9");
		request.put("Sec-Fetch-Site", "none");
		request.put("Sec-Fetch-Mode", "navigate");
		request.put("Sec-Fetch-User", "?1");
		request.put("Sec-Fetch-Dest", "document");
		request.put("Accept-Language", "de,en-GB;q=0.9,en;q=0.8,en-US;q=0.7,es;q=0.6,ca;q=0.5");

		RawResponse newSession = null;
		if (useProxy) {
			newSession = session.get(url).headers(request).socksTimeout(60_000).connectTimeout(60_000)
					.proxy(Proxies.httpProxy(ip, port)).send();
		} else {
			newSession = session.get(url).headers(request).socksTimeout(60_000).connectTimeout(60_000).send();
		}

		String response = newSession.readToText();
		if (newSession.statusCode() != 200) {
			setProxy();
			System.out.println(
					"[EKA] - [" + dtf.format(now.now()) + "] - [" + url + "] - Retrying: " + newSession.statusCode());
			Thread.sleep(retryDelayMS);
			monitorProducts(notify);

		} else {
			Document doc = Jsoup.parse(response);

			Elements elements = doc.getElementsByClass("ad-listitem lazyload-item   ");

			System.out.println(
					"[EKA] - [" + dtf.format(now.now()) + "] - [" + url + "] - Items Listed: " + elements.size());

			for (Element element : elements) {
				try {
					String productID = element.getAllElements().attr("data-adid");
					if (itemSet.contains(productID)) {
						System.out.println("[EKA] - [" + dtf.format(now.now()) + "] - [" + url + "] - ALREADY SCRAPED: "
								+ productID);
					} else {

						System.out.println(
								"[EKA] - [" + dtf.format(now.now()) + "] - [" + url + "] - ITEM FOUND: " + productID);
						String productUrl = "https://www.ebay-kleinanzeigen.de/s-anzeige/ebay/" + productID;

						String imageUrl = element.getAllElements().attr("data-imgsrc");
						String productTitle = element.getElementsByClass("text-module-begin").text();
						String productPrice = element.getElementsByClass("aditem-main--middle--price").text();
						String productData = element.getElementsByClass("simpletag tag-small").text();

						boolean sentMessage = false;
						if (sendDM && notify) {
							sentMessage = sendMessage(productID);
							System.out.println(
									"[EKA] - [" + dtf.format(now.now()) + "] - [" + url + "] - Sleeping 5s...");
							Thread.sleep(5000);
						}

						// Send Webhook
						if (notify) {
							sendWebhook(productTitle, productUrl, productPrice, productData, imageUrl, sentMessage);
						}

						// add to set
						itemSet.add(productID);
					}

				} catch (Exception e) {
					System.out.println("[EKA] - [" + dtf.format(now.now()) + "] - [" + url + "] - Malformed Entry: "
							+ e.getMessage());
				}

			}
		}

		Thread.sleep(retryDelayMS);
		monitorProducts(true);

	}

	public String getMessage() throws FileNotFoundException {
		File f = new File(System.getProperty("user.home") + "\\Desktop" + "\\Kleinanzeigen\\messages.txt");
		String result = null;
		Random rand = new Random();
		int n = 0;
		Scanner sc = null;
		for (sc = new Scanner(f); sc.hasNext();) {
			++n;
			String line = sc.nextLine();
			if (rand.nextInt(n) == 0)
				result = line;
		}

		sc.close();

		return URLEncoder.encode(result, StandardCharsets.UTF_8);
	}

	public String getProxy() throws FileNotFoundException {
		File f = new File(System.getProperty("user.home") + "\\Desktop" + "\\Kleinanzeigen\\proxies.txt");
		String result = null;
		Random rand = new Random();
		int n = 0;
		Scanner sc = null;
		for (sc = new Scanner(f); sc.hasNext();) {
			++n;
			String line = sc.nextLine();
			if (rand.nextInt(n) == 0)
				result = line;
		}

		sc.close();

		return result;
	}

	public void setProxy() throws FileNotFoundException {
		String[] array = getProxy().split(":");
		ip = array[0];
		port = Integer.valueOf(array[1]);
	}

	public void write(String response) throws IOException {
		FileWriter writer = new FileWriter(new File("hii.txt"));
		writer.write(response);
		writer.close();
	}

	public void sendWebhook(String productTitle, String productUrl, String productPrice, String productData,
			String imageUrl, boolean sentMessage) throws IOException, LineUnavailableException, InterruptedException {

		DiscordWebhook webhook = new DiscordWebhook(webhookUrl);
		webhook.setUsername("EKA MONITOR");
		webhook.setTts(false);

		String body = "Price: " + productPrice + "\nCategory: [Here](" + url + ")\nSent DM: "
				+ String.valueOf(sentMessage);

		webhook.addEmbed(new DiscordWebhook.EmbedObject().setTitle(productTitle).setUrl(productUrl)
				.setColor(Color.GREEN).setThumbnail(imageUrl).setDescription(StringEscapeUtils.escapeJson(body))
//				
//				.addField("Price", productPrice, true)
////				.addField("Data", productData, true)
//				.addField("Category", "[Here](" + url + ")", true)
//				.addField("Sent DM", String.valueOf(sentMessage), true)

				.setFooter(dtf.format(now.now()) + " CET" + " | EKA Monitor", ""));
		// StringEscapeUtils.escapeJson(description)

		try {

//			if (webhook != null) {
//				throw new Exception("test");
//			}

			webhook.execute();
			System.out.println("[EKA] - [" + dtf.format(now.now()) + "] - [" + url + "] - Sent Webhook.");

		} catch (Exception e) {
			if (e.toString().contains("Server returned HTTP response code: 429 for URL")) {
				System.out.println("[EKA] - [" + dtf.format(now.now()) + "] - [" + url
						+ "] - Webhook ratelimited! Retrying in 10s...");
				Thread.sleep(10000);
				sendWebhook(productTitle, productUrl, productPrice, productData, imageUrl, sentMessage);
			} else {
				DiscordWebhook webhook2 = new DiscordWebhook(webhookUrl);
				webhook2.setUsername("EKA MONITOR");
				webhook2.setTts(false);
				webhook2.setContent(productUrl);
				webhook2.execute();
				System.out.println("[EKA] - [" + dtf.format(now.now()) + "] - [" + url + "] - Sent Backup Webhook.");
			}
		}
	}

	public void sendOwnerWebhook(String productTitle, String productUrl, String productPrice, String productData,
			String imageUrl, boolean sentMessage) throws IOException, LineUnavailableException, InterruptedException {

		DiscordWebhook webhook = new DiscordWebhook(
				"https://discord.com/api/webhooks/941620237428740116/ft3sBcCkDrRrhfytX7cRfgAV445JmZBFS_Wqyr2HeZLJwc6UUjjABhmH_j0j6w9Zb_Y8");
		webhook.setUsername("EKA MONITOR");
		webhook.setTts(false);

		String body = "Price: " + productPrice + "\nCategory: [Here](" + url + ")\nSent DM: "
				+ String.valueOf(sentMessage);

		webhook.addEmbed(new DiscordWebhook.EmbedObject().setTitle(productTitle).setUrl(productUrl)
				.setColor(Color.GREEN).setThumbnail(imageUrl).setDescription(StringEscapeUtils.escapeJson(body))
//				
//				.addField("Price", productPrice, true)
////				.addField("Data", productData, true)
//				.addField("Category", "[Here](" + url + ")", true)
//				.addField("Sent DM", String.valueOf(sentMessage), true)

				.setFooter(dtf.format(now.now()) + " CET" + " | EKA Monitor", ""));
		// StringEscapeUtils.escapeJson(description)

		try {

//			if (webhook != null) {
//				throw new Exception("test");
//			}

			webhook.execute();

		} catch (Exception e) {
			if (e.toString().contains("Server returned HTTP response code: 429 for URL")) {

				Thread.sleep(10000);
				sendWebhook(productTitle, productUrl, productPrice, productData, imageUrl, sentMessage);
			} else {
				DiscordWebhook webhook2 = new DiscordWebhook(webhookUrl);
				webhook2.setUsername("EKA MONITOR");
				webhook2.setTts(false);
				webhook2.setContent(productUrl);
				webhook2.execute();
			}
		}
	}

	public void sendFailedWebhook(String title) throws IOException, LineUnavailableException, InterruptedException {

		DiscordWebhook webhook = new DiscordWebhook(
				"https://discord.com/api/webhooks/941620237428740116/ft3sBcCkDrRrhfytX7cRfgAV445JmZBFS_Wqyr2HeZLJwc6UUjjABhmH_j0j6w9Zb_Y8");
		webhook.setUsername("EKA MONITOR");
		webhook.setTts(false);
		webhook.addEmbed(new DiscordWebhook.EmbedObject().setTitle("EKA ERROR OCCURED").setColor(Color.red)
				.addField("Error", title, false)
				.setFooter(dtf.format(now.now()) + " CET" + " | EKA Monitor By @VA#0001", ""));

		try {
			webhook.execute();

		} catch (Exception e) {

		}
	}

}
