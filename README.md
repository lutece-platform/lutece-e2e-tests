# Lutece E2E Tests

Tests de non-régression end-to-end pour Lutece avec **Playwright** et **JUnit 5**.

## Vue d'ensemble du projet

Ce projet permet d'exécuter des tests E2E (End-to-End) sur une application Lutece de deux manières :

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         MODES D'EXÉCUTION                                   │
├─────────────────────────────────┬───────────────────────────────────────────┤
│      MODE EXTERNE               │       MODE TESTCONTAINERS                 │
│  (Instance Lutece existante)    │    (Environnement Docker isolé)           │
├─────────────────────────────────┼───────────────────────────────────────────┤
│                                 │                                           │
│  ┌──────────────┐               │   ┌─────────────────────────────────┐    │
│  │   Lutece     │               │   │      Réseau Docker isolé        │    │
│  │  (externe)   │               │   │  ┌─────────┐    ┌───────────┐  │    │
│  │  Port: 9080  │               │   │  │ MariaDB │◄───│  Lutece   │  │    │
│  └──────┬───────┘               │   │  │  :3306  │    │   :9090   │  │    │
│         │                       │   │  └─────────┘    └─────┬─────┘  │    │
│         │                       │   └───────────────────────┼────────┘    │
│         ▼                       │                           ▼              │
│  ┌──────────────┐               │                    ┌──────────────┐      │
│  │  Playwright  │               │                    │  Playwright  │      │
│  │   (Tests)    │               │                    │   (Tests)    │      │
│  └──────────────┘               │                    └──────────────┘      │
│                                 │                                           │
│  Classes: LoginTest,            │   Classes: LoginContainerTest,           │
│  AdminNavigationTest, etc.      │   ContainerIntegrationSuite              │
│                                 │                                           │
│  Commande:                      │   Commande:                               │
│  mvn test -Dtest=LoginTest      │   mvn test -Dtest=LoginContainerTest \   │
│                                 │     -Dlutece.image=mon-image:tag         │
└─────────────────────────────────┴───────────────────────────────────────────┘
```

## Architecture du projet

```
lutece-e2e-tests/
│
├── pom.xml                                    # Configuration Maven + dépendances
│
├── src/test/
│   ├── java/fr/paris/lutece/
│   │   │
│   │   ├── config/                            # Configuration des tests
│   │   │   ├── BaseTest.java                  # Base pour tests externes
│   │   │   └── ContainerBaseTest.java         # Base pour tests Testcontainers
│   │   │
│   │   ├── containers/                        # Conteneurs Docker personnalisés
│   │   │   └── LuteceContainer.java           # Conteneur Lutece/Open Liberty
│   │   │
│   │   ├── pages/                             # Page Object Model (POM)
│   │   │   ├── LoginPage.java                 # Page de connexion
│   │   │   ├── AdminMenuPage.java             # Menu administration
│   │   │   ├── WorkflowListPage.java          # Liste des workflows
│   │   │   ├── WorkflowEditPage.java          # Édition workflow
│   │   │   ├── FormsListPage.java             # Liste des formulaires
│   │   │   ├── FormsEditPage.java             # Édition formulaire
│   │   │   └── FormsFrontOfficePage.java      # Formulaire côté utilisateur
│   │   │
│   │   └── tests/                             # Classes de tests
│   │       ├── LoginTest.java                 # Tests connexion (externe)
│   │       ├── LoginContainerTest.java        # Tests connexion (Docker)
│   │       ├── RbacConfigurationTestt.java    # Configuration RBAC
│   │       ├── WorkflowCreationTest.java      # Tests création workflow
│   │       ├── FormsCreationTest.java         # Tests création formulaire
│   │       ├── FormsSubmissionTest.java       # Tests soumission formulaire
│   │       ├── CreationQuestionTypeTextLongTest.java  # Test standalone ajout question
│   │       ├── WorkflowFormsIntegrationSuite.java  # Suite externe (22 tests)
│   │       ├── ContainerSetup.java            # Démarrage conteneurs Docker
│   │       └── ContainerIntegrationSuite.java # Suite Docker (23 tests)
│   │
│   └── resources/
│       ├── META-INF/
│       │   └── microprofile-config.properties # Configuration MicroProfile
│       └── liberty/
│           └── server.xml                     # Config Open Liberty (référence)
│
└── target/
    ├── screenshots/                           # Captures d'écran des tests
    └── surefire-reports/                      # Rapports JUnit
```

## Pattern Page Object Model (POM)

Le projet utilise le pattern **Page Object Model** pour encapsuler les interactions avec l'interface utilisateur :

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          PAGE OBJECT MODEL                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   ┌─────────────┐         ┌─────────────┐         ┌─────────────┐          │
│   │  LoginPage  │────────►│AdminMenuPage│────────►│WorkflowList │          │
│   │             │ loginAs │             │ goTo... │    Page     │          │
│   │ - navigate  │         │ - isLogged  │         │ - create    │          │
│   │ - fillUser  │         │ - goToWkf   │         │ - activate  │          │
│   │ - fillPass  │         │ - goToForms │         │ - modify    │          │
│   │ - click     │         │             │         │             │          │
│   └─────────────┘         └─────────────┘         └─────────────┘          │
│                                                                             │
│   Avantages:                                                                │
│   ✓ Maintenabilité : Si l'UI change, modifier uniquement la Page           │
│   ✓ Réutilisabilité : Les actions sont encapsulées                         │
│   ✓ Lisibilité : loginPage.loginAs("admin", "pass") vs 10 lignes de code  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Suites de tests

### WorkflowFormsIntegrationSuite (Mode externe)

Suite complète de tests sur une instance Lutece existante :

```
┌─────────────────────────────────────────────────────────────────────────────┐
│              WorkflowFormsIntegrationSuite - FLUX D'EXÉCUTION               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   ┌───────────────────┐                                                    │
│   │ RbacConfiguration │  1. Configuration des rôles et permissions RBAC   │
│   │      Testt        │  2. Ajout des contrôles de ressources             │
│   │   (5 tests)       │  3. Configuration des droits utilisateur          │
│   └─────────┬─────────┘  4. Configuration des groupes de fonctionnalités  │
│             │                                                              │
│             ▼ (authentification sauvegardée)                               │
│   ┌───────────────────┐                                                    │
│   │ WorkflowCreation  │  1. Navigation vers la gestion des workflows      │
│   │      Test         │  2. Création d'un nouveau workflow                │
│   │   (7 tests)       │  3. Ajout de l'état initial                       │
│   └─────────┬─────────┘  4. Ajout de l'état final                         │
│             │            5. Ajout d'une action avec tâche                  │
│             │            6. Publication du workflow                        │
│             │            7. Activation du workflow                         │
│             ▼                                                              │
│   ┌───────────────────┐                                                    │
│   │  FormsCreation    │  1. Navigation vers la gestion des formulaires    │
│   │      Test         │  2. Création d'un nouveau formulaire              │
│   │   (9 tests)       │  3. Ajout des étapes (initiale + finale)          │
│   └─────────┬─────────┘  4. Ajout des questions (texte, nombre, date)     │
│             │            5. Association avec le workflow                   │
│             │            6. Configuration de la transition                 │
│             │            7. Publication du formulaire                      │
│             ▼                                                              │
│   ┌───────────────────┐                                                    │
│   │ FormsSubmission   │  1. Accès au formulaire (Front Office)            │
│   │      Test         │  2. Remplissage et soumission du formulaire       │
│   │   (1 test)        │  3. Vérification de la création de la réponse     │
│   └───────────────────┘                                                    │
│                                                                             │
│   Total: 22 tests | Durée: ~60-90 secondes                                 │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Exécution :**
```bash
mvn test -Dtest=WorkflowFormsIntegrationSuite \
  -Dlutece.base.url=http://localhost:9080/site-deontologie
```

### Tests standalone

#### CreationQuestionTypeTextLongTest

Test independant pour ajouter une question de type "Zone de texte long" a un formulaire existant.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│            CreationQuestionTypeTextLongTest - FLUX D'EXÉCUTION              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   ┌───────────────────────────────────────────────────────────────────┐    │
│   │  Configuration (microprofile-config.properties)                    │    │
│   │  ──────────────────────────────────────────────                    │    │
│   │  test.textlong.form.name = Forms Test integration 6379            │    │
│   │  test.textlong.step.name = Etape Initial                          │    │
│   │  test.textlong.question.title = Text long                         │    │
│   │  test.textlong.textarea.height = 500                              │    │
│   └───────────────────────────────────────────────────────────────────┘    │
│                           │                                                 │
│                           ▼                                                 │
│   ┌───────────────────────────────────────────────────────────────────┐    │
│   │  1. Login                                                          │    │
│   │     └── Connexion admin avec credentials configurés                │    │
│   └───────────────────────────────────────────────────────────────────┘    │
│                           │                                                 │
│                           ▼                                                 │
│   ┌───────────────────────────────────────────────────────────────────┐    │
│   │  2. Navigation                                                     │    │
│   │     ├── Accès à ManageForms.jsp                                    │    │
│   │     └── Clic sur le formulaire cible (par son nom)                 │    │
│   └───────────────────────────────────────────────────────────────────┘    │
│                           │                                                 │
│                           ▼                                                 │
│   ┌───────────────────────────────────────────────────────────────────┐    │
│   │  3. Selection étape                                                │    │
│   │     ├── Onglet "Etapes"                                            │    │
│   │     └── Clic sur "Modifier l'étape" de l'étape cible               │    │
│   └───────────────────────────────────────────────────────────────────┘    │
│                           │                                                 │
│                           ▼                                                 │
│   ┌───────────────────────────────────────────────────────────────────┐    │
│   │  4. Ajout question                                                 │    │
│   │     ├── Onglet "Liste des Questions"                               │    │
│   │     ├── Bouton "Ajouter une question"                              │    │
│   │     ├── Selection "Zone de texte long"                             │    │
│   │     ├── Saisie du titre                                            │    │
│   │     ├── Enregistrement                                             │    │
│   │     ├── Configuration hauteur zone de texte                        │    │
│   │     └── Enregistrement final                                       │    │
│   └───────────────────────────────────────────────────────────────────┘    │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Configuration :**

```properties
# META-INF/microprofile-config.properties

# Nom du formulaire cible
test.textlong.form.name=Forms Test integration 6379

# Nom de l'étape où ajouter la question
test.textlong.step.name=Etape Initial

# Titre de la question (optionnel, défaut: "Text long")
test.textlong.question.title=Text long

# Hauteur de la zone de texte (optionnel, défaut: "500")
test.textlong.textarea.height=500
```

**Exécution :**

```bash
# Exécution standard (utilise la config du fichier)
mvn test -Dtest=CreationQuestionTypeTextLongTest

# Avec paramètres en ligne de commande
mvn test -Dtest=CreationQuestionTypeTextLongTest \
    -Dtest.textlong.form.name="Mon Formulaire" \
    -Dtest.textlong.step.name="Etape 2" \
    -Dtest.textlong.question.title="Ma question"

# Mode visible (non headless) pour debug
mvn test -Dtest=CreationQuestionTypeTextLongTest -Dtest.headless=false
```

**Sortie du test :**

```
=== Configuration du test ===
Formulaire cible: Forms Test integration 6379
Etape cible: Etape Initial
Titre de la question: Text long
Hauteur zone de texte: 500
============================
=== Test termine avec succes ===
Question 'Text long' ajoutee au formulaire 'Forms Test integration 6379'
dans l'etape 'Etape Initial'
```

---

### ContainerIntegrationSuite (Mode Docker)

Suite complète réutilisant les mêmes classes de test mais dans un environnement Docker isolé :

```
┌─────────────────────────────────────────────────────────────────────────────┐
│             ContainerIntegrationSuite - ARCHITECTURE                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   @Suite                                                                    │
│   @SelectClasses({                                                          │
│       ContainerSetup.class,        ◄─── Démarre les conteneurs Docker      │
│       RbacConfigurationTestt.class,                                         │
│       WorkflowCreationTest.class,                                           │
│       FormsCreationTest.class,                                              │
│       FormsSubmissionTest.class                                             │
│   })                                                                        │
│                                                                             │
│   ┌─────────────────────────────────────────────────────────────────────┐  │
│   │                         ContainerSetup                               │  │
│   │                                                                      │  │
│   │  @BeforeAll                                                          │  │
│   │  ┌────────────────────────────────────────────────────────────────┐ │  │
│   │  │ 1. Network.newNetwork()                                        │ │  │
│   │  │ 2. MariaDBContainer.start()                                    │ │  │
│   │  │ 3. LuteceContainer.start()                                     │ │  │
│   │  │ 4. waitForApplication() - Attend que la page login fonctionne  │ │  │
│   │  │ 5. System.setProperty("lutece.base.url", dynamicUrl)           │ │  │
│   │  │ 6. BaseTest.updateBaseUrl(dynamicUrl) ◄── MET À JOUR BASE_URL  │ │  │
│   │  └────────────────────────────────────────────────────────────────┘ │  │
│   │                                                                      │  │
│   │  @Test testContainersRunning()                                       │  │
│   │  └── Vérifie que MariaDB et Lutece sont en cours d'exécution        │  │
│   │                                                                      │  │
│   └─────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│   Ensuite, les mêmes classes de test s'exécutent avec la nouvelle URL :    │
│   RbacConfigurationTestt → WorkflowCreationTest → FormsCreationTest → ...  │
│                                                                             │
│   Total: 23 tests | Durée: ~5 minutes (incluant démarrage conteneurs)      │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Exécution :**
```bash
mvn test -Dtest=ContainerIntegrationSuite \
  -Dlutece.image=nexus-docker/mon-image:1.0.0-SNAPSHOT \
  -Dlutece.context.root=/lutece \
  -Dtest.timeout=30000
```

### Partage de l'état d'authentification

Les tests partagent l'état d'authentification pour éviter de se reconnecter à chaque classe :

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    PARTAGE DE L'AUTHENTIFICATION                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   RbacConfigurationTestt                                                    │
│   ┌─────────────────────────────────────────────────────────────────────┐  │
│   │  @BeforeAll loginOnce()                                              │  │
│   │  ├── LoginPage.navigate()                                            │  │
│   │  ├── LoginPage.loginAs("admin", "adminadmin")                        │  │
│   │  └── saveAuthState() ──────────────────────┐                         │  │
│   └─────────────────────────────────────────────┼─────────────────────────┘  │
│                                                 │                            │
│                                                 ▼                            │
│                                    target/auth-state.json                    │
│                                    (cookies + localStorage)                  │
│                                                 │                            │
│   WorkflowCreationTest                          │                            │
│   ┌─────────────────────────────────────────────┼─────────────────────────┐  │
│   │  @BeforeAll loginOnce()                     │                         │  │
│   │  ├── if (hasAuthState())                    │                         │  │
│   │  │   └── createAuthenticatedContext() ◄────┘ (réutilise les cookies)  │  │
│   │  └── else LoginPage.loginAs(...)                                     │  │
│   └─────────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│   Avantages:                                                                │
│   ✓ Évite 4 connexions UI (une par classe de test)                         │
│   ✓ Tests plus rapides (~15 secondes économisées)                          │
│   ✓ Moins de charge sur le serveur                                         │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Alternative : Docker Compose

Le fichier `docker-compose.e2e.yml` permet d'exécuter les tests E2E dans un environnement Docker complet, sans utiliser Testcontainers.

### Architecture Docker Compose

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      docker-compose.e2e.yml                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   ┌─────────────┐      ┌─────────────────┐      ┌─────────────────┐        │
│   │   MariaDB   │◄─────│     Lutece      │◄─────│   Playwright    │        │
│   │     (db)    │ JDBC │  (Open Liberty) │ HTTP │    (tests)      │        │
│   │   :3306     │      │     :9080       │      │                 │        │
│   └─────────────┘      └─────────────────┘      └─────────────────┘        │
│                                                                             │
│   Services:                                                                 │
│   ├── db        : MariaDB 10.11 (base de données)                          │
│   ├── lutece    : Open Liberty + WAR Lutece (serveur d'application)        │
│   └── playwright: Conteneur Playwright Java (exécution des tests)          │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Comparaison Docker Compose vs Testcontainers

| Aspect | docker-compose.e2e.yml | Testcontainers |
|--------|------------------------|----------------|
| Orchestration | Docker Compose (YAML) | Code Java |
| Démarrage | `docker-compose up` | Automatique dans les tests |
| WAR Lutece | Monté via volume | Inclus dans l'image Docker |
| Configuration | Fichier YAML externe | Code Java |
| Ports | Fixes (3306, 9080) | Dynamiques (aléatoires) |
| Usage | CI/CD simple, debug local | Tests intégrés Maven |
| Isolation | Par projet Compose | Par exécution de test |

### Utilisation de Docker Compose

```bash
# Démarrer l'environnement complet (en arrière-plan)
docker-compose -f docker-compose.e2e.yml up -d

# Voir les logs en temps réel
docker-compose -f docker-compose.e2e.yml logs -f

# Voir les logs d'un service spécifique
docker-compose -f docker-compose.e2e.yml logs -f lutece

# Lancer les tests (dans le conteneur Playwright)
docker-compose -f docker-compose.e2e.yml run playwright

# Lancer les tests avec un WAR personnalisé
LUTECE_WAR_PATH=/chemin/vers/mon-site.war docker-compose -f docker-compose.e2e.yml up

# Arrêter et supprimer les conteneurs
docker-compose -f docker-compose.e2e.yml down

# Arrêter et supprimer les volumes (reset complet)
docker-compose -f docker-compose.e2e.yml down -v
```

### Quand utiliser Docker Compose ?

| Cas d'usage | Recommandation |
|-------------|----------------|
| Debug local avec environnement persistant | ✅ Docker Compose |
| CI/CD avec configuration simple | ✅ Docker Compose |
| Tests intégrés dans le build Maven | ✅ Testcontainers |
| Isolation complète par exécution | ✅ Testcontainers |
| Développement avec WAR local | ✅ Docker Compose |
| Tests sur image Docker pré-construite | ✅ Testcontainers |

### Configuration du WAR Lutece

Par défaut, le fichier cherche le WAR dans `./target/site-deontologie.war`. Pour utiliser un autre WAR :

```bash
# Via variable d'environnement
export LUTECE_WAR_PATH=/chemin/absolu/vers/mon-application.war
docker-compose -f docker-compose.e2e.yml up -d

# Ou directement dans la commande
LUTECE_WAR_PATH=../mon-projet/target/mon-site.war docker-compose -f docker-compose.e2e.yml up -d
```

---

## Tests avec Testcontainers

### Pourquoi Testcontainers ?

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    AVANTAGES DE TESTCONTAINERS                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ✓ Isolation totale      : Chaque test a son propre environnement          │
│  ✓ Reproductibilité      : Même environnement en local et en CI/CD         │
│  ✓ Pas de dépendances    : Pas besoin d'installer MariaDB localement       │
│  ✓ Base de données vierge: Chaque exécution part d'une base propre         │
│  ✓ Tests d'intégration   : Teste la vraie stack (Liberty + MariaDB)        │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Architecture Testcontainers

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         RÉSEAU DOCKER TESTCONTAINERS                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   ┌───────────────────────────────────────────────────────────────────┐    │
│   │                    Network: testcontainers-xxx                     │    │
│   │                                                                    │    │
│   │   ┌─────────────────────┐         ┌─────────────────────────┐    │    │
│   │   │      MariaDB        │         │    Lutece (Liberty)     │    │    │
│   │   │   mariadb:10.11     │◄────────│   Image personnalisée   │    │    │
│   │   │                     │  JDBC   │                         │    │    │
│   │   │   Alias: mariadb    │         │   Alias: lutece         │    │    │
│   │   │   Port interne: 3306│         │   Port interne: 9090    │    │    │
│   │   │   Base: core        │         │   Context: /lutece      │    │    │
│   │   │   User: lutece      │         │                         │    │    │
│   │   └─────────────────────┘         └───────────┬─────────────┘    │    │
│   │                                               │                   │    │
│   └───────────────────────────────────────────────┼───────────────────┘    │
│                                                   │                         │
│                                      Port mappé : │ localhost:XXXXX        │
│                                                   ▼                         │
│                                    ┌─────────────────────────┐              │
│                                    │       Playwright        │              │
│                                    │    (Chromium headless)  │              │
│                                    │                         │              │
│                                    │  URL: http://localhost  │              │
│                                    │       :XXXXX/lutece     │              │
│                                    └─────────────────────────┘              │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Séquence de démarrage (ContainerIntegrationSuite)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│              SÉQUENCE DE DÉMARRAGE - ContainerIntegrationSuite              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  ContainerSetup.startContainers() [@BeforeAll]                       │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│     │                                                                       │
│     ▼                                                                       │
│  1. Création réseau Docker ──────────────────────────── ~1 sec             │
│     │  Network.newNetwork()                                                │
│     │                                                                       │
│     ▼                                                                       │
│  2. Démarrage MariaDB ─────────────────────────────────  ~30 sec           │
│     │  MariaDBContainer("mariadb:10.11")                                   │
│     │    .withNetwork(network)                                             │
│     │    .withNetworkAliases("mariadb")                                    │
│     │    .withDatabaseName("core")                                         │
│     │    .withUsername("lutece")                                           │
│     │    .withPassword("lutece")                                           │
│     │  Healthcheck: JDBC SELECT 1 ◄── Nécessite mariadb-java-client!       │
│     │                                                                       │
│     ▼                                                                       │
│  3. Démarrage Lutece (Open Liberty) ───────────────────  ~40 sec           │
│     │  LuteceContainer(image, contextRoot)                                 │
│     │    .withSharedNetwork(network, "lutece")                             │
│     │    .withMariaDB("mariadb", 3306, "core", "lutece", "lutece")         │
│     │  Variables injectées:                                                │
│     │    portal.serverName=mariadb                                         │
│     │    portal.port=3306                                                  │
│     │    portal.dbname=core                                                │
│     │    portal.user=lutece                                                │
│     │    portal.password=lutece                                            │
│     │  Wait: log "CWWKF0011I" (Liberty ready)                              │
│     │                                                                       │
│     ▼                                                                       │
│  4. waitForApplication() ──────────────────────────────  ~30-120 sec       │
│     │  Boucle: retry toutes les 5 secondes (max 180s)                      │
│     │  │                                                                   │
│     │  └─► isApplicationReady()                                            │
│     │        HTTP GET /lutece/jsp/admin/AdminLogin.jsp                     │
│     │        Vérifie que la réponse contient "access_code"                 │
│     │        ou "password" (formulaire de login)                           │
│     │        ◄── Assure que Liquibase a fini les migrations!               │
│     │                                                                       │
│     ▼                                                                       │
│  5. Configuration URL dynamique ───────────────────────                    │
│     │  String baseUrl = lutece.getBaseURL()                                │
│     │  // → http://localhost:32XXX/lutece (port aléatoire)                 │
│     │                                                                       │
│     │  System.setProperty("lutece.base.url", baseUrl)                      │
│     │  BaseTest.updateBaseUrl(baseUrl) ◄── CRITIQUE pour les tests!       │
│     │                                                                       │
│     ▼                                                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  ContainerSetup.testContainersRunning() [@Test]                      │   │
│  │  - assertTrue(mariadb.isRunning())                                   │   │
│  │  - assertTrue(lutece.isRunning())                                    │   │
│  │  - assertTrue(baseUrl.contains("localhost"))                         │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│     │                                                                       │
│     ▼                                                                       │
│  6. Exécution RbacConfigurationTestt                                       │
│     │  BASE_URL est maintenant http://localhost:32XXX/lutece               │
│     │                                                                       │
│     ▼                                                                       │
│  7. Exécution WorkflowCreationTest, FormsCreationTest, ...                 │
│     │                                                                       │
│     ▼                                                                       │
│  8. Arrêt des conteneurs (shutdown hook automatique)                        │
│                                                                             │
│  Temps total: ~5 minutes                                                    │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Mécanisme de mise à jour de l'URL

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    MISE À JOUR DYNAMIQUE DE BASE_URL                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  PROBLÈME: Les classes de test héritent de BaseTest qui initialise         │
│  BASE_URL au chargement de la classe (avant ContainerSetup)                 │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  BaseTest.java (avant correction)                                    │   │
│  │  ─────────────────────────────────                                   │   │
│  │  protected static final String BASE_URL =                            │   │
│  │      config.getValue("lutece.base.url", String.class);               │   │
│  │  // → http://localhost:9080/site-deontologie (valeur du fichier)     │   │
│  │  // PROBLÈME: valeur fixée au chargement, jamais mise à jour!        │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  SOLUTION: Méthode updateBaseUrl() appelée par ContainerSetup               │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  BaseTest.java (corrigé)                                             │   │
│  │  ───────────────────────                                             │   │
│  │  protected static String BASE_URL =                                  │   │
│  │      config.getValue("lutece.base.url", String.class);               │   │
│  │                                                                      │   │
│  │  public static void updateBaseUrl(String url) {                      │   │
│  │      BASE_URL = url;  // ◄── Appelé par ContainerSetup               │   │
│  │  }                                                                   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  ContainerSetup.java                                                 │   │
│  │  ───────────────────                                                 │   │
│  │  String baseUrl = lutece.getBaseURL();                               │   │
│  │  // → http://localhost:32847/lutece                                  │   │
│  │                                                                      │   │
│  │  System.setProperty("lutece.base.url", baseUrl);                     │   │
│  │  BaseTest.updateBaseUrl(baseUrl);  // ◄── MET À JOUR TOUTES LES     │   │
│  │                                    //     CLASSES QUI HÉRITENT       │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  RÉSULTAT:                                                                  │
│  - Mode externe: BASE_URL = config (http://localhost:9080/site-xxx)        │
│  - Mode conteneur: BASE_URL = dynamique (http://localhost:32XXX/lutece)    │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Health Check de l'application

Le health check est crucial pour s'assurer que l'application est prête AVANT de lancer les tests :

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         HEALTH CHECK INTELLIGENT                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  PROBLÈME: Liberty dit "CWWKF0011I: Server ready" mais l'application       │
│  n'est pas encore prête (Liquibase migre la base de données)               │
│                                                                             │
│  Timeline typique:                                                          │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │  T+0s    │ Liberty démarre                                           │  │
│  │  T+37s   │ CWWKF0011I: Server ready ◄── Container "started"          │  │
│  │  T+40s   │ Liquibase: Running Changeset create_db_lutece_core.sql    │  │
│  │  T+60s   │ Liquibase: Creating tables...                              │  │
│  │  T+90s   │ Liquibase: Running plugin changesets...                    │  │
│  │  T+120s  │ Application lutece started ◄── VRAIMENT PRÊTE             │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  SOLUTION: Vérifier que la page de login fonctionne                        │
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │  LuteceContainer.isApplicationReady()                                │  │
│  │                                                                       │  │
│  │  1. HTTP GET /lutece/jsp/admin/AdminLogin.jsp                        │  │
│  │     └── Timeout: 10 secondes                                         │  │
│  │                                                                       │  │
│  │  2. Vérifier responseCode == 200                                     │  │
│  │                                                                       │  │
│  │  3. Lire le contenu de la réponse                                    │  │
│  │                                                                       │  │
│  │  4. Vérifier que le HTML contient:                                   │  │
│  │     - "access_code" (champ de login)                                 │  │
│  │     - "password" (champ de mot de passe)                             │  │
│  │     - "AdminLogin" (marqueur de page)                                │  │
│  │                                                                       │  │
│  │  5. Si trouvé → return true (app prête)                              │  │
│  │     Sinon    → return false (retry dans 5s)                          │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  Avantages:                                                                 │
│  ✓ Détecte quand la base de données est initialisée                        │
│  ✓ Évite les erreurs "table not found" dans les tests                      │
│  ✓ Fiable même si le serveur répond 500 pendant l'initialisation           │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Configuration de l'image Lutece

L'image Docker doit être configurée pour recevoir les paramètres de connexion via variables d'environnement :

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    CONFIGURATION OPEN LIBERTY (server.xml)                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   <!-- Variables avec valeurs par défaut, surchargées par l'environnement -->
│   <variable name="portal.serverName" defaultValue="mariadb"/>              │
│   <variable name="portal.port"       defaultValue="3306"/>                 │
│   <variable name="portal.dbname"     defaultValue="core"/>                 │
│   <variable name="portal.user"       defaultValue="lutece"/>               │
│   <variable name="portal.password"   defaultValue="lutece"/>               │
│                                                                             │
│   <!-- DataSource utilisant ces variables -->                               │
│   <dataSource jndiName="jdbc/portal">                                      │
│       <jdbcDriver libraryRef="mariadbLib"/>                                │
│       <properties.mariadb                                                  │
│           serverName="${portal.serverName}"                                │
│           portNumber="${portal.port}"                                      │
│           databaseName="${portal.dbname}"                                  │
│           user="${portal.user}"                                            │
│           password="${portal.password}"/>                                  │
│   </dataSource>                                                            │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘

       ▲                                           │
       │                                           │
       │  Le conteneur LuteceContainer             │
       │  injecte ces variables:                   │
       │                                           ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         LuteceContainer.java                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   public LuteceContainer withMariaDB(String host, int port,                │
│                                       String db, String user, String pass) │
│   {                                                                        │
│       withEnv("portal.serverName", host);    // → mariadb                  │
│       withEnv("portal.port", String.valueOf(port));  // → 3306             │
│       withEnv("portal.dbname", db);          // → core                     │
│       withEnv("portal.user", user);          // → lutece                   │
│       withEnv("portal.password", pass);      // → lutece                   │
│       return this;                                                         │
│   }                                                                        │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Installation et prérequis

### Prérequis

- Java 17+
- Maven 3.8+
- Docker (pour les tests Testcontainers)
- Accès au registry Docker (pour les images Lutece)

### Installation

```bash
# Cloner le projet
cd lutece-e2e-tests

# Installer les dépendances et les navigateurs Playwright
mvn clean install -DskipTests
```

## Exécution des tests

### Tests sur instance externe

```bash
# Suite complète workflow + formulaires (22 tests)
mvn test -Dtest=WorkflowFormsIntegrationSuite \
  -Dlutece.base.url=http://localhost:9080/site-deontologie

# Test de connexion uniquement
mvn test -Dtest=LoginTest

# Tests RBAC uniquement
mvn test -Dtest=RbacConfigurationTestt \
  -Dlutece.base.url=http://localhost:9080/site-deontologie

# Tests workflow uniquement
mvn test -Dtest=WorkflowCreationTest \
  -Dlutece.base.url=http://localhost:9080/site-deontologie
```

### Tests avec Testcontainers

```bash
# Suite complète d'intégration avec Docker (23 tests)
mvn test -Dtest=ContainerIntegrationSuite \
  -Dlutece.image=nexus-docker-fastdeploy.api.paris.mdp/bild/f98/site-deontologie:1.0.0-SNAPSHOT \
  -Dlutece.context.root=/lutece \
  -Dtest.timeout=30000

# Test de connexion avec Docker
mvn test -Dtest=LoginContainerTest \
  -Dlutece.image=rafikyahiaoui/lutece-site-8 \
  -Dlutece.context.root=/lutece

# Avec image personnalisée et debug visuel (non headless)
mvn test -Dtest=ContainerIntegrationSuite \
  -Dlutece.image=mon-registry/mon-image:tag \
  -Dlutece.context.root=/lutece \
  -Dtest.headless=false \
  -Dtest.timeout=30000
```

### Propriétés de configuration

| Propriété | Description | Valeur par défaut |
|-----------|-------------|-------------------|
| `lutece.base.url` | URL de l'instance externe | http://localhost:9080/site-deontologie |
| `lutece.image` | Image Docker Lutece | rafikyahiaoui/lutece-site-8 |
| `lutece.context.root` | Context root de l'application | /lutece |
| `lutece.db.password` | Mot de passe base de données | lutece |
| `test.headless` | Mode sans interface (headless) | true |
| `test.timeout` | Timeout par action en ms | 10000 (externe) / 30000 (conteneur) |
| `test.slowmo` | Délai entre actions (debug) | 0 |

### Exemples de commandes complètes

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     EXEMPLES DE COMMANDES                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  MODE EXTERNE (instance Lutece déjà démarrée)                              │
│  ─────────────────────────────────────────────                              │
│                                                                             │
│  # Tests rapides sur instance locale                                        │
│  mvn test -Dtest=WorkflowFormsIntegrationSuite \                           │
│    -Dlutece.base.url=http://localhost:9080/site-deontologie                │
│                                                                             │
│  # Tests sur environnement de recette                                       │
│  mvn test -Dtest=WorkflowFormsIntegrationSuite \                           │
│    -Dlutece.base.url=https://recette.monsite.fr/lutece                     │
│                                                                             │
│  MODE CONTENEUR (Docker isolé)                                              │
│  ─────────────────────────────                                              │
│                                                                             │
│  # Tests avec image Nexus                                                   │
│  mvn test -Dtest=ContainerIntegrationSuite \                               │
│    -Dlutece.image=nexus-docker-fastdeploy.api.paris.mdp/bild/f98/site-deontologie:1.0.0-SNAPSHOT \
│    -Dlutece.context.root=/lutece \                                         │
│    -Dtest.timeout=30000                                                    │
│                                                                             │
│  # Tests avec image publique                                                │
│  mvn test -Dtest=ContainerIntegrationSuite \                               │
│    -Dlutece.image=rafikyahiaoui/lutece-site-8 \                            │
│    -Dlutece.context.root=/lutece \                                         │
│    -Dtest.timeout=30000                                                    │
│                                                                             │
│  # Debug mode (affiche le navigateur)                                       │
│  mvn test -Dtest=LoginContainerTest \                                      │
│    -Dlutece.image=rafikyahiaoui/lutece-site-8 \                            │
│    -Dtest.headless=false \                                                 │
│    -Dtest.slowmo=500                                                       │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Dépendances clés

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           DÉPENDANCES MAVEN                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   Playwright (Tests E2E)                                                   │
│   └── com.microsoft.playwright:playwright:1.41.0                           │
│                                                                             │
│   JUnit 5 (Framework de tests)                                             │
│   └── org.junit.jupiter:junit-jupiter:5.10.2                               │
│                                                                             │
│   Testcontainers (Conteneurs Docker)                                       │
│   ├── org.testcontainers:testcontainers:1.19.7                             │
│   ├── org.testcontainers:junit-jupiter:1.19.7                              │
│   └── org.testcontainers:mariadb:1.19.7                                    │
│                                                                             │
│   MariaDB JDBC Driver ⚠️ IMPORTANT                                         │
│   └── org.mariadb.jdbc:mariadb-java-client:3.3.3                           │
│       │                                                                    │
│       └── Requis pour le healthcheck de Testcontainers !                   │
│           Testcontainers utilise JDBC pour vérifier que MariaDB            │
│           est prêt AVANT de démarrer Lutece.                               │
│           Sans ce driver → ClassNotFoundException                          │
│                                                                             │
│   MicroProfile Config (Configuration)                                      │
│   └── io.smallrye.config:smallrye-config:3.5.4                             │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Flux d'un test de connexion

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     FLUX DU TEST LoginContainerTest                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   @BeforeAll                                                                │
│   ┌─────────────────────────────────────────────────────────────────────┐  │
│   │  1. Démarrer MariaDB (via @Container)                               │  │
│   │  2. Démarrer LuteceContainer avec variables portal.*                │  │
│   │  3. Attendre que l'application soit prête (healthcheck HTTP)        │  │
│   │  4. Démarrer Playwright (Chromium headless)                         │  │
│   └─────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│   @Test testLoginSuccess()                                                  │
│   ┌─────────────────────────────────────────────────────────────────────┐  │
│   │  1. Naviguer vers /jsp/admin/AdminLogin.jsp                         │  │
│   │  2. Screenshot "container-login-page.png"                           │  │
│   │  3. Remplir "admin" / "adminadmin"                                  │  │
│   │  4. Cliquer "Se connecter"                                          │  │
│   │  5. Screenshot "container-after-login.png"                          │  │
│   │  6. Vérifier que l'URL contient "AdminMenu.jsp"                     │  │
│   └─────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│   @AfterAll                                                                 │
│   ┌─────────────────────────────────────────────────────────────────────┐  │
│   │  1. Fermer Playwright                                               │  │
│   │  2. Arrêter LuteceContainer                                         │  │
│   │  3. MariaDB arrêté automatiquement par Testcontainers               │  │
│   └─────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Intégration CI/CD

### Pipeline Jenkins

Le projet inclut un `Jenkinsfile` complet supportant deux modes d'exécution :

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         PIPELINE JENKINS                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                        PARAMÈTRES                                    │   │
│  ├─────────────────────────────────────────────────────────────────────┤   │
│  │  TEST_MODE      : TESTCONTAINERS / EXTERNAL                         │   │
│  │  LUTECE_IMAGE   : Image Docker à tester (mode TESTCONTAINERS)       │   │
│  │  ENVIRONMENT    : recette / preprod / local (mode EXTERNAL)         │   │
│  │  TEST_SUITE     : LoginContainerTest / ContainerIntegrationSuite    │   │
│  │  HEADLESS       : true / false                                      │   │
│  │  SONAR_ANALYSIS : true / false                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                         STAGES                                       │   │
│  ├─────────────────────────────────────────────────────────────────────┤   │
│  │                                                                      │   │
│  │  1. Checkout                                                         │   │
│  │     │                                                                │   │
│  │     ▼                                                                │   │
│  │  2. Verify Environment (Java, Maven, Docker)                         │   │
│  │     │                                                                │   │
│  │     ├──────────────────────┬─────────────────────────┐              │   │
│  │     │                      │                         │              │   │
│  │     ▼ [TESTCONTAINERS]     ▼ [EXTERNAL]              │              │   │
│  │  3a. Docker Login       3b. Determine URL            │              │   │
│  │     │                      │                         │              │   │
│  │     ▼                      │                         │              │   │
│  │  4a. Pull Lutece Image     │                         │              │   │
│  │     │                      │                         │              │   │
│  │     └──────────────────────┴─────────────────────────┘              │   │
│  │                      │                                               │   │
│  │                      ▼                                               │   │
│  │  5. Install Dependencies                                             │   │
│  │     │                                                                │   │
│  │     ▼                                                                │   │
│  │  6. Install Playwright Browsers                                      │   │
│  │     │                                                                │   │
│  │     ▼                                                                │   │
│  │  7. Run E2E Tests                                                    │   │
│  │     │                                                                │   │
│  │     ▼                                                                │   │
│  │  8. SonarQube Analysis (si SONAR_ANALYSIS=true)                      │   │
│  │     │                                                                │   │
│  │     ▼                                                                │   │
│  │  9. Quality Gate                                                     │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Prérequis Jenkins

| Composant | Configuration |
|-----------|---------------|
| Agent | Label `docker` avec Docker installé |
| JDK | Tool `JDK-17` configuré |
| Maven | Tool `Maven-3.9` configuré |
| Docker Credentials | ID `nexus-docker-credentials` |
| SonarQube Scanner | Tool `SonarScanner` configuré |
| SonarQube Server | Server `SonarQube` configuré |

### Intégration SonarQube

Les rapports de tests E2E sont envoyés à SonarQube pour le suivi qualité :

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      INTÉGRATION SONARQUBE                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   ┌─────────────────┐         ┌─────────────────┐         ┌─────────────┐  │
│   │  Tests E2E      │         │    Jenkins      │         │  SonarQube  │  │
│   │  (Playwright)   │────────►│   Pipeline      │────────►│   Server    │  │
│   └─────────────────┘         └─────────────────┘         └─────────────┘  │
│                                                                             │
│   Données envoyées:                                                        │
│   ├── Résultats des tests JUnit (target/surefire-reports/*.xml)            │
│   ├── Code source des tests (src/test/java)                                │
│   └── Classes compilées (target/test-classes)                              │
│                                                                             │
│   Métriques disponibles dans SonarQube:                                    │
│   ├── Nombre de tests                                                      │
│   ├── Tests réussis / échoués / ignorés                                    │
│   ├── Durée d'exécution                                                    │
│   ├── Historique des exécutions                                            │
│   └── Quality Gate status                                                  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### Configuration SonarQube dans le Jenkinsfile

```groovy
withSonarQubeEnv('SonarQube') {
    sh """
        mvn sonar:sonar \
            -Dsonar.projectKey=lutece-e2e-tests \
            -Dsonar.projectName='Lutece E2E Tests' \
            -Dsonar.sources=src/test/java \
            -Dsonar.tests=src/test/java \
            -Dsonar.java.binaries=target/test-classes \
            -Dsonar.junit.reportPaths=target/surefire-reports
    """
}
```

#### Paramètres SonarQube

| Paramètre | Valeur | Description |
|-----------|--------|-------------|
| `sonar.projectKey` | lutece-e2e-tests | Clé unique du projet |
| `sonar.projectName` | Lutece E2E Tests | Nom affiché dans SonarQube |
| `sonar.sources` | src/test/java | Code source à analyser |
| `sonar.tests` | src/test/java | Répertoire des tests |
| `sonar.java.binaries` | target/test-classes | Classes compilées |
| `sonar.junit.reportPaths` | target/surefire-reports | Rapports JUnit |

### Exécution du pipeline

#### Mode Testcontainers (environnement Docker isolé)

```
TEST_MODE       = TESTCONTAINERS
LUTECE_IMAGE    = nexus-docker/mon-image:1.0.0-SNAPSHOT
TEST_SUITE      = LoginContainerTest
SONAR_ANALYSIS  = true
```

#### Mode External (instance existante)

```
TEST_MODE       = EXTERNAL
ENVIRONMENT     = recette
TEST_SUITE      = LoginTest
SONAR_ANALYSIS  = true
```

### Artefacts archivés

| Artefact | Chemin | Description |
|----------|--------|-------------|
| Screenshots | `target/screenshots/**/*.png` | Captures d'écran des tests |
| Rapports JUnit | `target/surefire-reports/**/*` | Résultats XML des tests |
| Logs conteneurs | `target/container-logs.txt` | Logs Docker (en cas d'échec) |

## Dépannage

### Erreur "ClassNotFoundException: org.mariadb.jdbc.Driver"

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  PROBLÈME: Le driver JDBC MariaDB n'est pas dans le classpath des tests    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Cause: Testcontainers a besoin du driver pour faire le healthcheck        │
│         de MariaDB AVANT de démarrer les tests.                            │
│                                                                             │
│  Solution: Ajouter dans pom.xml :                                          │
│                                                                             │
│    <dependency>                                                            │
│        <groupId>org.mariadb.jdbc</groupId>                                 │
│        <artifactId>mariadb-java-client</artifactId>                        │
│        <version>3.3.3</version>                                            │
│        <scope>test</scope>                                                 │
│    </dependency>                                                           │
│                                                                             │
│  Note: Ce driver est requis même si l'image Docker Lutece contient         │
│        déjà le driver MySQL/MariaDB !                                      │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Erreur "Container startup failed for image"

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  PROBLÈME: L'image Docker n'est pas accessible                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Vérifications:                                                            │
│  1. L'image existe : docker pull mon-registry/mon-image:tag                │
│  2. Authentification : docker login mon-registry                           │
│  3. Propriété correcte : -Dlutece.image=mon-registry/mon-image:tag         │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Erreur "Lutece non disponible après 180 secondes"

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  PROBLÈME: L'application Lutece ne démarre pas correctement                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Vérifications:                                                            │
│  1. Regarder les logs du conteneur (affichés avec préfixe [lutece])        │
│  2. Vérifier les erreurs de connexion BDD                                  │
│  3. Vérifier que les variables portal.* sont correctes                     │
│  4. Vérifier que le schéma de base existe dans l'image                     │
│                                                                             │
│  Configuration attendue:                                                   │
│  - Port: 9090 (pas 9080)                                                   │
│  - Context: /lutece (pas /site-xxx)                                        │
│  - Variables: portal.serverName, portal.port, portal.dbname, etc.          │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Erreur "Timeout 10000ms exceeded" dans les tests

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  PROBLÈME: Les tests échouent avec des timeouts en mode conteneur          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Cause: Le timeout par défaut (10s) est trop court pour les conteneurs     │
│         car l'application est plus lente (base de données fraîche,         │
│         pas de cache, etc.)                                                │
│                                                                             │
│  Solution: Augmenter le timeout pour les tests en conteneur                │
│                                                                             │
│    mvn test -Dtest=ContainerIntegrationSuite \                             │
│      -Dlutece.image=... \                                                  │
│      -Dtest.timeout=30000   ◄── 30 secondes au lieu de 10                  │
│                                                                             │
│  Note: Le timeout s'applique à CHAQUE action Playwright (click, fill...)   │
│        pas à l'ensemble du test.                                           │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Erreur "BASE_URL pointe vers l'ancienne valeur"

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  PROBLÈME: Les tests utilisent http://localhost:9080/site-deontologie      │
│            au lieu de l'URL dynamique du conteneur                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Cause: ContainerSetup n'a pas mis à jour BaseTest.BASE_URL                │
│                                                                             │
│  Vérification: Dans les logs, chercher:                                    │
│    "=== Environnement prêt - URL: http://localhost:32XXX/lutece ==="       │
│    "Tests utiliseront l'URL: http://localhost:32XXX/lutece"                │
│                                                                             │
│  Si vous voyez:                                                            │
│    "Tests utiliseront l'URL: http://localhost:9080/site-deontologie"       │
│  → Le problème est que BaseTest.updateBaseUrl() n'est pas appelé           │
│                                                                             │
│  Solution: Vérifier ContainerSetup.java contient:                          │
│    BaseTest.updateBaseUrl(baseUrl);                                        │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Le context root n'est pas correct

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  PROBLÈME: Tests échouent car l'URL ne correspond pas au context root      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Symptôme: Page 404 ou page d'erreur Liberty                               │
│                                                                             │
│  Comment trouver le bon context root:                                       │
│  1. Regarder les logs du conteneur Lutece                                  │
│  2. Chercher la ligne:                                                      │
│     "[AUDIT] CWWKT0016I: Web application available: http://xxx:9090/yyy/"  │
│     → Le context root est "/yyy"                                           │
│                                                                             │
│  Exemples de context roots courants:                                        │
│  - /lutece          ← Image standard                                       │
│  - /site-deontologie ← Site spécifique                                     │
│  - /lutece-core      ← Core Lutece                                         │
│                                                                             │
│  Solution: Utiliser -Dlutece.context.root=/bon-context                     │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Récapitulatif des suites de tests

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    COMPARAISON DES SUITES DE TESTS                          │
├──────────────────────────┬────────────────────────┬─────────────────────────┤
│                          │  WorkflowForms         │  Container              │
│                          │  IntegrationSuite      │  IntegrationSuite       │
├──────────────────────────┼────────────────────────┼─────────────────────────┤
│  Mode                    │  Instance externe      │  Docker isolé           │
├──────────────────────────┼────────────────────────┼─────────────────────────┤
│  Prérequis               │  Lutece démarré        │  Docker installé        │
│                          │  manuellement          │                         │
├──────────────────────────┼────────────────────────┼─────────────────────────┤
│  Base de données         │  Existante (avec       │  Vierge (créée par      │
│                          │  données)              │  Liquibase)             │
├──────────────────────────┼────────────────────────┼─────────────────────────┤
│  Nombre de tests         │  22                    │  23 (+ContainerSetup)   │
├──────────────────────────┼────────────────────────┼─────────────────────────┤
│  Durée                   │  ~60-90 secondes       │  ~5 minutes             │
├──────────────────────────┼────────────────────────┼─────────────────────────┤
│  Timeout recommandé      │  10000 ms              │  30000 ms               │
├──────────────────────────┼────────────────────────┼─────────────────────────┤
│  Isolation               │  Non                   │  Oui (complète)         │
├──────────────────────────┼────────────────────────┼─────────────────────────┤
│  CI/CD                   │  Nécessite instance    │  Autonome               │
│                          │  préexistante          │                         │
├──────────────────────────┼────────────────────────┼─────────────────────────┤
│  Reproductibilité        │  Dépend de l'env       │  100% reproductible     │
├──────────────────────────┼────────────────────────┼─────────────────────────┤
│  Usage recommandé        │  Développement local   │  CI/CD, validation      │
│                          │  Tests rapides         │  pré-release            │
└──────────────────────────┴────────────────────────┴─────────────────────────┘
```

## Quick Reference

```bash
# ═══════════════════════════════════════════════════════════════════════════
#                           QUICK REFERENCE
# ═══════════════════════════════════════════════════════════════════════════

# --- MODE EXTERNE (instance existante) ---
mvn test -Dtest=WorkflowFormsIntegrationSuite \
  -Dlutece.base.url=http://localhost:9080/site-deontologie

# --- MODE CONTENEUR (Docker isolé) ---
mvn test -Dtest=ContainerIntegrationSuite \
  -Dlutece.image=nexus-docker-fastdeploy.api.paris.mdp/bild/f98/site-deontologie:1.0.0-SNAPSHOT \
  -Dlutece.context.root=/lutece \
  -Dtest.timeout=30000

# --- DEBUG (navigateur visible) ---
mvn test -Dtest=LoginContainerTest \
  -Dlutece.image=rafikyahiaoui/lutece-site-8 \
  -Dtest.headless=false \
  -Dtest.slowmo=500

# --- PARAMÈTRES IMPORTANTS ---
# -Dlutece.base.url=...      URL instance externe
# -Dlutece.image=...         Image Docker
# -Dlutece.context.root=...  Context root (/lutece, /site-xxx)
# -Dtest.timeout=...         Timeout par action (ms)
# -Dtest.headless=...        true/false (afficher navigateur)
# -Dtest.slowmo=...          Délai entre actions (ms, pour debug)
```

## Lancement dans Jenkins

### Configuration du projet Jenkins

#### Option A : Pipeline Multibranch (recommandé)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    CONFIGURATION MULTIBRANCH PIPELINE                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  1. New Item → Multibranch Pipeline                                        │
│                                                                             │
│  2. Branch Sources → Git                                                    │
│     ├── Repository URL: https://gitlab.paris.mdp/.../lutece-e2e-tests.git  │
│     └── Credentials: (vos credentials GitLab)                              │
│                                                                             │
│  3. Build Configuration                                                     │
│     ├── Mode: by Jenkinsfile                                               │
│     └── Script Path: Jenkinsfile                                           │
│                                                                             │
│  4. Scan Multibranch Pipeline Triggers                                      │
│     └── Periodically if not otherwise run: 1 hour                          │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### Option B : Pipeline classique

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      CONFIGURATION PIPELINE SIMPLE                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  1. New Item → Pipeline                                                     │
│                                                                             │
│  2. Definition: Pipeline script from SCM                                    │
│     ├── SCM: Git                                                           │
│     ├── Repository URL: https://gitlab.paris.mdp/.../lutece-e2e-tests.git  │
│     ├── Credentials: (vos credentials GitLab)                              │
│     ├── Branch: */main                                                     │
│     └── Script Path: Jenkinsfile                                           │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Prérequis Jenkins

#### Tools à configurer (Manage Jenkins → Tools)

| Tool | Name | Configuration |
|------|------|---------------|
| JDK | `JDK-17` | Install from adoptium.net ou chemin local |
| Maven | `Maven-3.9` | Install from Apache ou chemin local |
| SonarQube Scanner | `SonarScanner` | Install automatiquement |

#### Credentials à créer (Manage Jenkins → Credentials)

| ID | Type | Usage |
|----|------|-------|
| `nexus-docker-credentials` | Username/Password | Accès au registry Docker Nexus |

#### Plugins requis

- Pipeline
- Docker Pipeline
- Git
- JUnit
- SonarQube Scanner
- AnsiColor
- Timestamps

### Lancement manuel (Build with Parameters)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        BUILD WITH PARAMETERS                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  TEST_MODE:        [TESTCONTAINERS ▼]                                      │
│                                                                             │
│  LUTECE_IMAGE:     [nexus-docker-fastdeploy.api.paris.mdp/bild/f98/site-  │
│                     deontologie:1.0.0-SNAPSHOT                    ]        │
│                                                                             │
│  ENVIRONMENT:      [recette ▼]  (ignoré si TESTCONTAINERS)                 │
│                                                                             │
│  LUTECE_URL:       [                                              ]        │
│                    (URL personnalisée, override ENVIRONMENT)               │
│                                                                             │
│  TEST_SUITE:       [ContainerIntegrationSuite ▼]                           │
│                                                                             │
│  HEADLESS:         [✓] Exécuter en mode headless                           │
│                                                                             │
│  SONAR_ANALYSIS:   [✓] Envoyer les rapports à SonarQube                    │
│                                                                             │
│                              [ Build ]                                      │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Exemples de configurations Jenkins

#### Test d'une nouvelle image Docker (CI/CD)

```
TEST_MODE       = TESTCONTAINERS
LUTECE_IMAGE    = nexus-docker-fastdeploy.api.paris.mdp/bild/f98/site-deontologie:1.0.0-SNAPSHOT
TEST_SUITE      = ContainerIntegrationSuite
HEADLESS        = true
SONAR_ANALYSIS  = true
```

#### Test sur environnement de recette

```
TEST_MODE       = EXTERNAL
ENVIRONMENT     = recette
TEST_SUITE      = WorkflowFormsIntegrationSuite
HEADLESS        = true
SONAR_ANALYSIS  = true
```

#### Test rapide (login uniquement)

```
TEST_MODE       = TESTCONTAINERS
LUTECE_IMAGE    = rafikyahiaoui/lutece-site-8
TEST_SUITE      = LoginContainerTest
HEADLESS        = true
SONAR_ANALYSIS  = false
```

### Intégration avec un pipeline de build

Pour lancer les tests E2E automatiquement après le build d'une image Docker :

```groovy
// Dans le Jenkinsfile de votre projet Lutece
stage('Build Docker Image') {
    steps {
        sh 'mvn package -Pdocker'
        sh 'docker push nexus-docker/.../mon-site:${VERSION}'
    }
}

stage('Run E2E Tests') {
    steps {
        build job: 'lutece-e2e-tests',
              parameters: [
                  string(name: 'TEST_MODE', value: 'TESTCONTAINERS'),
                  string(name: 'LUTECE_IMAGE', value: "nexus-docker/.../mon-site:${VERSION}"),
                  string(name: 'TEST_SUITE', value: 'ContainerIntegrationSuite'),
                  booleanParam(name: 'HEADLESS', value: true),
                  booleanParam(name: 'SONAR_ANALYSIS', value: true)
              ],
              wait: true,
              propagate: true  // Échoue si les tests échouent
    }
}
```

### Visualisation des résultats

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          RÉSULTATS DU BUILD                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Console Output                                                             │
│  ├── Logs complets de l'exécution                                          │
│  └── Logs des conteneurs Docker (préfixe [lutece])                         │
│                                                                             │
│  Test Results (JUnit)                                                       │
│  ├── Tests: 23                                                             │
│  ├── Passed: 23                                                            │
│  ├── Failed: 0                                                             │
│  └── Durée: 5m 12s                                                         │
│                                                                             │
│  Artifacts                                                                  │
│  ├── target/screenshots/*.png   ← Captures d'écran                         │
│  ├── target/surefire-reports/*  ← Rapports XML                             │
│  └── target/container-logs.txt  ← Logs Docker (si échec)                   │
│                                                                             │
│  SonarQube                                                                  │
│  └── Lien vers le dashboard SonarQube                                      │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Déclenchement automatique

#### Webhook GitLab → Jenkins

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         WEBHOOK GITLAB                                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  GitLab Project → Settings → Webhooks                                       │
│                                                                             │
│  URL: https://jenkins.paris.mdp/project/lutece-e2e-tests                   │
│                                                                             │
│  Triggers:                                                                  │
│  [✓] Push events                                                           │
│  [✓] Merge request events                                                  │
│  [ ] Tag push events                                                       │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### Cron (exécution planifiée)

Dans la configuration du pipeline Jenkins :

```groovy
triggers {
    // Tous les jours à 6h du matin
    cron('H 6 * * *')

    // Ou après chaque push
    pollSCM('H/5 * * * *')
}
```

## Ressources

- [Documentation Playwright Java](https://playwright.dev/java/docs/intro)
- [Documentation Lutece](https://dev.lutece.paris.fr/)
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [Testcontainers Documentation](https://www.testcontainers.org/)
- [Testcontainers MariaDB Module](https://www.testcontainers.org/modules/databases/mariadb/)
- [Open Liberty Documentation](https://openliberty.io/docs/)
- [Jenkins Pipeline Syntax](https://www.jenkins.io/doc/book/pipeline/syntax/)
