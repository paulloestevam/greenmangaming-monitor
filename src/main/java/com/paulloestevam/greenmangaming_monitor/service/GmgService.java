package com.paulloestevam.greenmangaming_monitor.service;

import com.paulloestevam.greenmangaming_monitor.config.GmgConfig;
import com.paulloestevam.greenmangaming_monitor.model.Game;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
@Slf4j
public class GmgService {

    private final JavaMailSender mailSender;
    private final GmgConfig gmgConfig;

    private static final String LOG_DIR = "logs";

    @Value("${webdriver.chrome.driver-path:}")
    private String chromeDriverPath;

    @Value("${email.sender}")
    private String emailSender;

    @Value("${minDiscountPercentage:70}")
    private Integer minDiscountPercentage;

    public GmgService(JavaMailSender mailSender, GmgConfig gmgConfig) {
        this.mailSender = mailSender;
        this.gmgConfig = gmgConfig;
    }

    public void fetchDeals() {
        if (chromeDriverPath != null && !chromeDriverPath.isBlank()) {
            System.setProperty("webdriver.chrome.driver", chromeDriverPath);
        }

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36");

        WebDriver driver = null;

        try {
            driver = new ChromeDriver(options);
            List<String> urls = gmgConfig.getUrls();
            List<Game> allGamesFound = new ArrayList<>();

            for (String url : urls) {
                log.info("Acessando: " + url);
                driver.get(url);
                driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(15));
                performScroll(driver);

                String pageSource = driver.getPageSource();
                saveDebugHtml(pageSource);

                Document document = Jsoup.parse(pageSource);

                Elements productCards = document.select("li[ng-repeat*='product in'], li.product-card-container");

                if (productCards.isEmpty()) {
                    Elements tags = document.select("gmgprice[type=discount]");
                    for (Element tag : tags) {
                        processGmgTag(tag, url, allGamesFound, document);
                    }
                } else {
                    for (Element card : productCards) {
                        processCard(card, url, allGamesFound, document);
                    }
                }
            }

            if (!allGamesFound.isEmpty()) {
                allGamesFound.sort(Comparator.comparingInt(Game::getDiscountPercentage).reversed());
                log.info("Total de ofertas: {}", allGamesFound.size());

                String emailBody = generatePlainTextEmail(allGamesFound);
                String lastLogContent = getLastLogContent();
                saveLogFile(emailBody);

                if (isNewContent(lastLogContent, emailBody)) {
                    log.info("Novas ofertas detectadas! Enviando e-mail...");


                    String subject = "Green Man Gaming - " + allGamesFound.size() + " Ofertas do dia";
                    sendEmail(emailSender, subject, emailBody);
                } else {
                    log.info("Nenhuma mudança nas ofertas em relação ao último log. E-mail não enviado.");
                }

            } else {
                log.warn("Nenhuma oferta encontrada acima de {}%.", minDiscountPercentage);
            }

        } catch (Exception e) {
            log.error("Erro fatal no crawler", e);
        } finally {
            if (driver != null) driver.quit();
        }
    }

    private boolean isNewContent(String lastLogContent, String newBody) {
        if (lastLogContent.isEmpty()) return true;

        String newCoreContent = newBody;
        String oldCoreContent = lastLogContent;

        return !newCoreContent.equals(oldCoreContent);
    }

    private String getLastLogContent() {
        try {
            Path dir = Paths.get(LOG_DIR);
            if (!Files.exists(dir)) return "";

            try (Stream<Path> stream = Files.list(dir)) {
                Optional<Path> lastLog = stream
                        .filter(f -> !Files.isDirectory(f))
                        .filter(f -> f.getFileName().toString().startsWith("log_") && f.toString().endsWith(".txt"))
                        .max(Comparator.comparing(Path::getFileName));

                if (lastLog.isPresent()) {
                    log.info("📂 Último log carregado para comparação: " + lastLog.get().getFileName());
                    return Files.readString(lastLog.get(), StandardCharsets.UTF_8);
                }
            }
        } catch (IOException e) {
            log.error("Erro ao ler último log", e);
        }
        return "";
    }


    private String generatePlainTextEmail(List<Game> games) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < games.size(); i++) {
            Game game = games.get(i);
            String discountStr = game.getDiscountPercentage() + "%";

            String line = String.format("%2d %7s  %-4s  %.30s",
                    i + 1,
                    game.getCurrentPrice(),
                    discountStr,
                    game.getTitle()
            );
            sb.append(line).append("\n");
        }
        return sb.toString();
    }

    private void createLogDirectoryIfNotExists(Path dirPath) throws IOException {
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }
    }

    private void saveLogFile(String content) {
        try {
            Path logDir = Paths.get(LOG_DIR);
            createLogDirectoryIfNotExists(logDir);

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = "log_" + timestamp + ".txt";
            Path filePath = logDir.resolve(fileName);

            Files.writeString(filePath, content, StandardCharsets.UTF_8);
            log.info("Log salvo em: " + filePath.toAbsolutePath());
        } catch (IOException e) {
            log.error("Erro ao salvar log", e);
        }
    }

    private void saveDebugHtml(String html) {
        try {
            Path logDir = Paths.get(LOG_DIR);
            createLogDirectoryIfNotExists(logDir);
            Path filePath = logDir.resolve("debug_source.html");
            Files.writeString(filePath, html, StandardCharsets.UTF_8);
        } catch (IOException e) {
        }
    }

    private void processCard(Element card, String url, List<Game> allGamesFound, Document fullDoc) {
        try {
            String discountText = getDiscountText(card);
            if (discountText.isEmpty()) return;

            int discount = Integer.parseInt(discountText);
            if (discount >= minDiscountPercentage) {
                String title = getTitle(card);
                String currentPrice = getPrice(card, "currentPrice");
                String originalPrice = getPrice(card, "rrp");

                String link = url;
                Element linkTag = card.selectFirst("a");
                if (linkTag != null && linkTag.hasAttr("href")) link = linkTag.attr("href");
                if (!link.startsWith("http")) link = "https://www.greenmangaming.com" + link;

                String imageUrl = findImage(card, fullDoc, title);

                allGamesFound.add(new Game(title, currentPrice, originalPrice, discount, link, imageUrl));
            }
        } catch (Exception e) {
        }
    }

    private void processGmgTag(Element tag, String url, List<Game> allGamesFound, Document fullDoc) {
        try {
            String txt = tag.text().replaceAll("[^0-9]", "");
            if (!txt.isEmpty() && Integer.parseInt(txt) >= minDiscountPercentage) {
                Element parent = findProductContainer(tag);
                if (parent != null) {
                    processCard(parent, url, allGamesFound, fullDoc);
                }
            }
        } catch (Exception e) {
        }
    }

    private Element findProductContainer(Element child) {
        Element current = child.parent();
        int levels = 0;
        while (current != null && levels < 15) {
            String tag = current.tagName().toLowerCase();
            String className = current.className().toLowerCase();
            if (tag.equals("li") || className.contains("product-card") || className.contains("module") || current.hasAttr("ng-controller")) {
                return current;
            }
            current = current.parent();
            levels++;
        }
        return child.parent();
    }

    private String findImage(Element card, Document fullDoc, String title) {
        String url = searchImgInElement(card);
        if (isValidUrl(url)) return fixUrl(url);

        if (card.parent() != null) {
            url = searchImgInElement(card.parent());
            if (isValidUrl(url)) return fixUrl(url);
        }

        if (title != null && title.length() > 3) {
            Elements allImgs = fullDoc.select("img[alt*='" + title.replace("'", "") + "']");
            for (Element img : allImgs) {
                url = getSrc(img);
                if (isValidUrl(url)) return fixUrl(url);
            }
        }
        return "https://placehold.co/300x300?text=No+Image";
    }

    private String searchImgInElement(Element el) {
        Element mediaImg = el.selectFirst(".media-object img");
        if (mediaImg != null) return getSrc(mediaImg);

        Elements imgs = el.select("img");
        for (Element img : imgs) {
            String src = getSrc(img);
            if (isValidUrl(src)) return src;
        }
        return null;
    }

    private String getSrc(Element img) {
        if (isValidUrl(img.attr("ng-src"))) return img.attr("ng-src");
        if (isValidUrl(img.attr("src"))) return img.attr("src");
        if (isValidUrl(img.attr("data-src"))) return img.attr("data-src");
        return null;
    }

    private String getDiscountText(Element card) {
        Element tag = card.selectFirst("gmgprice[type=discount]");
        String text = (tag != null) ? tag.text() : "";
        if (text.isEmpty()) {
            Element badge = card.selectFirst(".discount-badge, .label-discount");
            if (badge != null) text = badge.text();
        }
        return text.replaceAll("[^0-9]", "").trim();
    }

    private String getTitle(Element card) {
        Element tag = card.selectFirst("gmgprice[type=discount]");
        if (tag != null && tag.hasAttr("sku")) return tag.attr("sku");
        Element titleEl = card.selectFirst(".prod-name, .product-title, h3, a.ng-binding");
        return titleEl != null ? titleEl.text() : "Jogo Desconhecido";
    }

    private String getPrice(Element card, String type) {
        Element tag = card.selectFirst("gmgprice[type=" + type + "]");
        if (tag != null) return tag.text();
        if (type.equals("currentPrice")) {
            Element el = card.selectFirst(".current-price, .price-current");
            return el != null ? el.text() : "---";
        } else {
            Element el = card.selectFirst(".prev-price, .rrp");
            return el != null ? el.text() : "---";
        }
    }

    private boolean isValidUrl(String url) {
        return url != null && url.length() > 5 && !url.contains("base64");
    }

    private String fixUrl(String url) {
        if (url.startsWith("//")) return "https:" + url;
        return url;
    }

    private void performScroll(WebDriver driver) {
        try {
            long lastHeight = (long) ((JavascriptExecutor) driver).executeScript("return document.body.scrollHeight");
            for (int i = 0; i < 6; i++) {
                ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, document.body.scrollHeight);");
                Thread.sleep(2000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void sendEmail(String to, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setFrom(emailSender);
            helper.setText(body, false);
            mailSender.send(message);
            log.info("E-mail enviado com sucesso!");
        } catch (MessagingException e) {
            log.error("Erro ao enviar e-mail: " + e.getMessage());
        }
    }
}