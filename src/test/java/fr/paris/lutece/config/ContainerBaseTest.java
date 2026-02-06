package fr.paris.lutece.config;

import fr.paris.lutece.containers.LuteceContainer;
import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Classe de base pour les tests E2E avec Testcontainers.
 * Démarre automatiquement MariaDB et Lutece dans des conteneurs Docker.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class ContainerBaseTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerBaseTest.class);

    // Configuration
    protected static final int TIMEOUT = 30000;
    protected static final int VIEWPORT_WIDTH = 1920;
    protected static final int VIEWPORT_HEIGHT = 1080;
    protected static final String LOCALE = "fr-FR";
    protected static final String SCREENSHOTS_PATH = "target/screenshots";

    // Réseau partagé entre les conteneurs
    protected static final Network NETWORK = Network.newNetwork();

    // Conteneur MariaDB
    @Container
    protected static final MariaDBContainer<?> MARIADB = new MariaDBContainer<>("mariadb:10.11")
        .withNetwork(NETWORK)
        .withNetworkAliases("mariadb")
        .withDatabaseName("lutece")
        .withUsername("lutece")
        .withPassword("lutece")
        .withInitScript("sql/init-lutece.sql"); // Script d'initialisation optionnel

    // Conteneur Lutece (démarré après MariaDB)
    protected static LuteceContainer luteceContainer;

    // Playwright
    protected static Playwright playwright;
    protected static Browser browser;
    protected BrowserContext context;
    protected Page page;

    // URL de base dynamique
    protected static String BASE_URL;

    // Chemin pour sauvegarder l'état d'authentification
    private static final java.nio.file.Path AUTH_STATE_PATH =
        java.nio.file.Paths.get("target/auth-state.json");

    @BeforeAll
    static void startContainersAndBrowser() {
        LOGGER.info("=== Démarrage des conteneurs Testcontainers ===");

        // Démarrer MariaDB d'abord (géré par @Container)
        LOGGER.info("MariaDB démarré sur: {}:{}",
            MARIADB.getHost(), MARIADB.getMappedPort(3306));

        // Démarrer Lutece avec la connexion à MariaDB
        luteceContainer = new LuteceContainer()
            .withSharedNetwork(NETWORK, "lutece")
            .withMariaDB(
                "mariadb",           // Alias réseau de MariaDB
                3306,                // Port interne MariaDB
                "lutece",            // Nom de la base
                "lutece",            // Utilisateur
                "lutece"             // Mot de passe
            );

        luteceContainer.start();

        BASE_URL = luteceContainer.getBaseURL();
        LOGGER.info("Lutece démarré sur: {}", BASE_URL);

        // Attendre que l'application soit vraiment prête
        waitForApplicationReady();

        // Démarrer Playwright
        LOGGER.info("=== Démarrage de Playwright ===");
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
            .setHeadless(true)
            .setSlowMo(0));

        LOGGER.info("=== Environnement de test prêt ===");
    }

    private static void waitForApplicationReady() {
        LOGGER.info("Attente que Lutece soit prêt...");
        int maxAttempts = 60;
        for (int i = 0; i < maxAttempts; i++) {
            if (luteceContainer.isApplicationReady()) {
                LOGGER.info("Lutece est prêt après {} secondes", i * 2);
                return;
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for Lutece", e);
            }
        }
        throw new RuntimeException("Lutece n'est pas prêt après " + (maxAttempts * 2) + " secondes");
    }

    @AfterAll
    static void stopContainersAndBrowser() {
        LOGGER.info("=== Arrêt de l'environnement de test ===");

        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
        if (luteceContainer != null && luteceContainer.isRunning()) {
            luteceContainer.stop();
        }
        // MariaDB est arrêté automatiquement par @Container
    }

    /**
     * Crée un nouveau contexte de navigateur.
     */
    protected BrowserContext createContext() {
        return browser.newContext(new Browser.NewContextOptions()
            .setViewportSize(VIEWPORT_WIDTH, VIEWPORT_HEIGHT)
            .setLocale(LOCALE)
            .setIgnoreHTTPSErrors(true));
    }

    /**
     * Crée un contexte avec l'état d'authentification sauvegardé.
     */
    protected BrowserContext createAuthenticatedContext() {
        if (!java.nio.file.Files.exists(AUTH_STATE_PATH)) {
            return createContext();
        }
        return browser.newContext(new Browser.NewContextOptions()
            .setViewportSize(VIEWPORT_WIDTH, VIEWPORT_HEIGHT)
            .setLocale(LOCALE)
            .setIgnoreHTTPSErrors(true)
            .setStorageStatePath(AUTH_STATE_PATH));
    }

    /**
     * Sauvegarde l'état d'authentification pour réutilisation.
     */
    protected void saveAuthState() {
        if (context != null) {
            context.storageState(new BrowserContext.StorageStateOptions()
                .setPath(AUTH_STATE_PATH));
            LOGGER.info("État d'authentification sauvegardé");
        }
    }

    /**
     * Prend une capture d'écran.
     */
    protected void takeScreenshot(String name) {
        if (page != null) {
            java.nio.file.Path path = java.nio.file.Paths.get(SCREENSHOTS_PATH, name + ".png");
            try {
                java.nio.file.Files.createDirectories(path.getParent());
                page.screenshot(new Page.ScreenshotOptions()
                    .setPath(path)
                    .setFullPage(true));
                LOGGER.info("Screenshot sauvegardé: {}", path);
            } catch (Exception e) {
                LOGGER.warn("Impossible de sauvegarder le screenshot: {}", e.getMessage());
            }
        }
    }

    /**
     * Retourne l'URL de base de l'application.
     */
    protected String getBaseUrl() {
        return BASE_URL;
    }
}
