import java.io.File;
import java.io.FileNotFoundException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.devtools.v87.log.model.LogEntry;
import org.openqa.selenium.devtools.v87.network.model.Headers;
import org.openqa.selenium.devtools.v87.network.model.Request;
import org.openqa.selenium.devtools.v87.network.model.Request.ReferrerPolicy;
import org.openqa.selenium.devtools.v87.network.model.ResourcePriority;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxDriverService;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.remote.DesiredCapabilities;

import com.twocaptcha.TwoCaptcha;
import com.twocaptcha.captcha.ReCaptcha;

import net.dongliu.requests.Proxies;
import net.dongliu.requests.RawResponse;
import net.dongliu.requests.Requests;
import net.dongliu.requests.Session;

public class LoginTask extends Thread {

	private String email;
	private String password;
	private String twoCaptchaKey;
	private Set<org.openqa.selenium.Cookie> cookiesList;
	private String csrfToken;

	private long expiryTime = 0;
	private String url = "https://www.ebay-kleinanzeigen.de/m-einloggen.html";
	private static DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
	private static LocalDateTime now = LocalDateTime.now();

	public LoginTask(String email, String password, String twoCaptchaKey) {
		this.email = email;
		this.password = password;
		this.twoCaptchaKey = twoCaptchaKey;
	}

	public void run() {

		while (true) {
			try {
				long unixTime = Instant.now().getEpochSecond();
				if (expiryTime == 0 || unixTime > expiryTime) {
					getLoginCookie();
				}
				Thread.sleep(15000);
			} catch (Exception e) {
				System.out.println(
						"[EKA] - [" + dtf.format(now.now()) + "] - [" + url + "] - LoginTask Error: " + e.toString());
			}
		}

	}

	public void getLoginCookie() throws Exception {

		System.out.println("[EKA] - [" + dtf.format(now.now()) + "] - [" + url + "] - Attempting Login...");
//		System.err.close();
		ChromeOptions options = new ChromeOptions();
		options.addArguments("--disable-blink-features=AutomationControlled", "--log-level=3");

		// options.addArguments("--headless");

		DesiredCapabilities capabilities = new DesiredCapabilities();
		capabilities.setCapability(ChromeOptions.CAPABILITY, options);
		options.merge(capabilities);

		ChromeDriverService service = new ChromeDriverService.Builder()
				.usingDriverExecutable(
						new File(System.getProperty("user.home") + "\\Desktop" + "\\Kleinanzeigen\\chromedriver2.exe"))
				.usingAnyFreePort().build();
		WebDriver driver = new ChromeDriver(service, options);
//		driver.manage().window().minimize();
		ArrayList<String> tabs = new ArrayList<String>(driver.getWindowHandles());

//		// 

		try {

			// ---------------------------------
			JavascriptExecutor js = (JavascriptExecutor) driver;

			// Perform Click on LOGIN button using JavascriptExecutor
			driver.get("https://www.premint.xyz/");
			String response = (String) js.executeScript("fetch(\"https://www.premint.xyz/dashboard/\", {\n"
					+ "  \"headers\": {\n"
					+ "    \"accept\": \"text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9\",\n"
					+ "    \"accept-language\": \"de,en-GB;q=0.9,en;q=0.8,en-US;q=0.7,es;q=0.6,ca;q=0.5\",\n"
					+ "    \"cache-control\": \"max-age=0\",\n"
					+ "    \"sec-ch-ua\": \"\\\" Not A;Brand\\\";v=\\\"99\\\", \\\"Chromium\\\";v=\\\"100\\\", \\\"Google Chrome\\\";v=\\\"100\\\"\",\n"
					+ "    \"sec-ch-ua-mobile\": \"?0\",\n" + "    \"sec-ch-ua-platform\": \"\\\"Windows\\\"\",\n"
					+ "    \"sec-fetch-dest\": \"document\",\n" + "    \"sec-fetch-mode\": \"navigate\",\n"
					+ "    \"sec-fetch-site\": \"none\",\n" + "    \"sec-fetch-user\": \"?1\",\n"
					+ "\"Access-Control-Allow-Origin\": \"*\",\n" + "    \"upgrade-insecure-requests\": \"1\"\n"
					+ "  },\n" + "  \"referrerPolicy\": \"strict-origin-when-cross-origin\",\n" + "  \"body\": null,\n"
					+ "  \"method\": \"GET\",\n" + "  \"mode\": \"cors\",\n" + "  \"credentials\": \"include\"\n"
					+ "})\n" + ".then(response=>{return response.text();});");
			LogEntries entry = driver.manage().logs().get(LogType.BROWSER);
			// Retrieving all log
			List<org.openqa.selenium.logging.LogEntry> logs = entry.getAll();
			// Print one by one
			for (org.openqa.selenium.logging.LogEntry e : logs) {
				System.out.println(e);
			}

			Thread.sleep(1000000);
			// ---------------------------------

			driver.get(url);

			Thread.sleep(8000);

			if (driver.findElements(By.xpath("/html/body/div[2]/div/div/div/div/div[3]/button[1]/span/span"))
					.size() != 0) {

				driver.findElement(By.xpath("/html/body/div[2]/div/div/div/div/div[3]/button[1]/span/span")).click();
			}

			driver.findElement(By.xpath("//*[@id=\"login-email\"]")).sendKeys(email);
			driver.findElement(By.xpath("//*[@id=\"login-password\"]")).sendKeys(password);

			try {
				// get Captcha
				String[] temp = driver.getPageSource().split(Pattern.quote("onRecaptchaResponse\" data-sitekey=\""));
				String temp2 = temp[1];
				String[] temp3 = temp2.split(Pattern.quote("\""));
				String siteKey = temp3[0];

				String token = requestCaptcha(driver.getCurrentUrl(), siteKey);

				JavascriptExecutor jse = (JavascriptExecutor) driver;
				jse.executeScript("document.getElementById(\"g-recaptcha-response\").innerHTML=\"" + token + "\";");
			} catch (Exception e) {
				System.out.println("[EKA] - [" + dtf.format(now.now()) + "] - [" + url + "] - Captcha Missing...");

			}

			driver.findElement(By.xpath("//*[@id=\"login-submit\"]")).click();

			int counter = 0;
			while (true) {
				if (!driver.getPageSource().contains("name=\"_csrf\" content=\"")) {
					Thread.sleep(2000);
					counter++;
				} else {
					// Get csrf
					String[] temp = driver.getPageSource().split(Pattern.quote("name=\"_csrf\" content=\""));
					String temp2 = temp[1];
					String[] temp3 = temp2.split(Pattern.quote("\""));
					this.csrfToken = temp3[0];
					break;
				}

				if (counter > 10) {
					throw new Exception("LOGIN_CSRF_MISSING");
				}

			}

			boolean found = false;

			for (org.openqa.selenium.Cookie cookie : driver.manage().getCookies()) {
				if (cookie.getName().equals("access_token")) {
					String value = cookie.getValue();
					this.expiryTime = cookie.getExpiry().getTime() - 30;
					this.cookiesList = driver.manage().getCookies();
					found = true;
				}
			}

			if (!found) {
				throw new Exception("COOKIE_MISSING");
			}

			driver.close();

			System.out.println("[EKA] - [" + dtf.format(now.now()) + "] - [" + url + "] - Logged In!");
//			System.setErr(System.out);

		}

		catch (Exception e) {
			driver.close();
			e.printStackTrace();
			System.out
					.println("[EKA] - [" + dtf.format(now.now()) + "] - [" + url + "] - Cookie Error: " + e.toString());
			Thread.sleep(3000);
			getLoginCookie();
		}

	}

	public Set<org.openqa.selenium.Cookie> getCookiesList() {
		return cookiesList;
	}

	public String getCsrfToken() {
		return csrfToken;
	}

	public String requestCaptcha(String captchaUrl, String siteKey) throws Exception {
		TwoCaptcha solver = new TwoCaptcha(twoCaptchaKey);
		solver.setDefaultTimeout(120);
		solver.setRecaptchaTimeout(60);
		solver.setPollingInterval(10);

		ReCaptcha captcha = new ReCaptcha();
		captcha.setSiteKey(siteKey);
		captcha.setUrl(captchaUrl);
		captcha.setInvisible(true);
		captcha.setAction("verify");
		captcha.setScore(0.9);
		String response = "";

		try {
			System.out.println("[EKA] - [" + dtf.format(now.now()) + "] - [" + url + "] - Requesting Captcha...");
			String captchaId = solver.send(captcha);

			while (solver.getResult(captchaId) == null) {
				System.out.println("[EKA] - [" + dtf.format(now.now()) + "] - [" + url + "] - Waiting For Captcha...");
				Thread.sleep(15000);
			}

			response = solver.getResult(captchaId);
			System.out.println("[EKA] - [" + dtf.format(now.now()) + "] - [" + url + "] - Received Captcha!");

		} catch (Exception e) {
			System.out.println(
					"[EKA] - [" + dtf.format(now.now()) + "] - [" + url + "] - Captcha Error: " + e.toString());
			Thread.sleep(2500);
			requestCaptcha(captchaUrl, siteKey);

		}
		return response;

	}

}
