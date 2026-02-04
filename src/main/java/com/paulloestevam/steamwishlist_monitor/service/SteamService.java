package com.paulloestevam.steamwishlist_monitor.service;

import com.paulloestevam.steamwishlist_monitor.config.SteamWishlistConfig;
import com.paulloestevam.steamwishlist_monitor.model.Game;
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
import java.util.*;
import java.util.stream.Stream;

@Service
@Slf4j
public class SteamService {

    private final JavaMailSender mailSender;
    private final SteamWishlistConfig config;
    private static final String LOG_DIR = "logs";

    @Value("${webdriver.chrome.driver-path:}")
    private String chromeDriverPath;

    @Value("${email.sender}")
    private String emailSender;

    @Value("${minDiscountPercentage:70}")
    private Integer minDiscountPercentage;

    public SteamService(JavaMailSender mailSender, SteamWishlistConfig config) {
        this.mailSender = mailSender;
        this.config = config;
    }

    public void fetchDeals() {
        if (chromeDriverPath != null && !chromeDriverPath.isBlank()) {
            System.setProperty("webdriver.chrome.driver", chromeDriverPath);
        }

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless", "--no-sandbox", "--disable-dev-shm-usage");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36");

        WebDriver driver = null;

        try {
            driver = new ChromeDriver(options);
            List<String> urls = config.getUrls();

            // Usamos um Map para garantir que cada URL de jogo apareça apenas uma vez
            Map<String, Game> uniqueGames = new HashMap<>();

            for (String url : urls) {
                log.info("Acessando wishlist: " + url);
                driver.get(url);
                driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(15));

                performScroll(driver);

                String pageSource = driver.getPageSource();
                saveDebugHtml(pageSource);

                Document document = Jsoup.parse(pageSource);

                // Buscamos diretamente os elementos que contêm o aria-label de desconto
                Elements priceElements = document.select("[aria-label*='de desconto']");

                for (Element priceEl : priceElements) {
                    processPriceElement(priceEl, url, uniqueGames);
                }
            }

            List<Game> allGamesFound = new ArrayList<>(uniqueGames.values());

            if (!allGamesFound.isEmpty()) {
                allGamesFound.sort(Comparator.comparingInt(Game::getDiscountPercentage).reversed());
                log.info("Total de ofertas: {}", allGamesFound.size());

                String emailBody = generatePlainTextEmail(allGamesFound);
                String lastLogContent = getLastLogContent();
                saveLogFile(emailBody);

                if (!lastLogContent.trim().equals(emailBody.trim())) {
                    log.info("Novas ofertas detectadas! Enviando e-mail...");
                    sendEmail(emailSender, "Steam Wishlist - " + allGamesFound.size() + " Ofertas", emailBody);
                } else {
                    log.info("Nenhuma mudança nas ofertas em relação ao último log. E-mail não enviado.");
                }
            } else {
                log.warn("Nenhuma oferta acima de {}% encontrada.", minDiscountPercentage);
            }

        } catch (Exception e) {
            log.error("Erro fatal:", e);
        } finally {
            if (driver != null) driver.quit();
        }
    }

    private void processPriceElement(Element priceEl, String baseUrl, Map<String, Game> uniqueGames) {
        try {
            String label = priceEl.attr("aria-label");
            int discount = Integer.parseInt(label.split("%")[0].replaceAll("[^0-9]", ""));

            if (discount >= minDiscountPercentage) {
                // Para evitar duplicados, subimos até encontrar o container que tem o link do jogo
                Element container = priceEl.parent();
                Element titleEl = null;

                // Sobe até 10 níveis para achar o link do jogo associado a esse preço
                for (int i = 0; i < 10 && container != null; i++) {
                    titleEl = container.selectFirst("a[href*='/app/']:not(:has(img))");
                    if (titleEl != null) break;
                    container = container.parent();
                }

                if (titleEl != null) {
                    String title = titleEl.text().trim();
                    String link = titleEl.attr("href").split("\\?")[0]; // Limpa parâmetros do link

                    // Se já processamos este link, ignoramos para não repetir
                    if (uniqueGames.containsKey(link)) return;

                    String originalPrice = "---";
                    String currentPrice = "---";

                    if (label.contains("Preço normal:") && label.contains("Com desconto:")) {
                        originalPrice = label.split("Preço normal:")[1].split("Com desconto:")[0].replace(".", "").trim();
                        currentPrice = label.split("Com desconto:")[1].trim();
                    }

                    uniqueGames.put(link, new Game(title, currentPrice, originalPrice, discount, link, ""));
                }
            }
        } catch (Exception e) {
            // Silencioso para erros de parsing em elementos irrelevantes
        }
    }

    private void performScroll(WebDriver driver) {
        try {
            for (int i = 0; i < 8; i++) {
                ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, document.body.scrollHeight);");
                Thread.sleep(2000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String generatePlainTextEmail(List<Game> games) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < games.size(); i++) {
            Game game = games.get(i);
            String discountStr = game.getDiscountPercentage() + "%";

            String rawPrice = game.getCurrentPrice();
            String numberOnly = "0"; // Valor padrão caso venha nulo

            if (rawPrice != null) {
                numberOnly = rawPrice
                        .replace("CLP", "")
                        .replace("$", "")       // Remove o cifrão para alinhar nós mesmos
                        .replaceAll("\\.$", "") // Remove ponto se estiver no final
                        .trim();
            }

            String line = String.format("%2d %7s %-4s %.30s",
                    i + 1,
                    numberOnly,
                    discountStr,
                    game.getTitle()
            );
            sb.append(line).append("\n");
        }

        return sb.toString();
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

    private void createLogDirectoryIfNotExists(Path dirPath) throws IOException {
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }
    }

    private void saveDebugHtml(String html) {
        try {
            Files.createDirectories(Paths.get(LOG_DIR));
            Files.writeString(Paths.get(LOG_DIR, "debug_source.html"), html);
        } catch (IOException e) {
        }
    }

    private void sendEmail(String to, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setFrom(emailSender);
            helper.setText(body, false);
            mailSender.send(message);
            log.info("E-mail enviado!");
        } catch (MessagingException e) {
            log.error("Erro e-mail", e);
        }
    }
}