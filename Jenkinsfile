/**
 * Pipeline Jenkins pour les tests E2E Lutece
 *
 * Deux modes d'exécution:
 * - EXTERNAL: Tests sur une instance Lutece existante (recette, preprod, etc.)
 * - TESTCONTAINERS: Tests avec environnement Podman isolé (MariaDB + Lutece)
 *
 * Prérequis pour le mode TESTCONTAINERS:
 * - Agent Jenkins avec Podman installé (label: 'podman')
 * - Accès au registry pour l'image Lutece
 */

pipeline {
    agent {
        label 'podman'  // Agent avec Podman disponible
    }

    parameters {
        // Mode d'exécution
        choice(
            name: 'TEST_MODE',
            choices: ['TESTCONTAINERS', 'EXTERNAL'],
            description: 'Mode d\'exécution: TESTCONTAINERS (Docker isolé) ou EXTERNAL (instance existante)'
        )

        // Configuration Testcontainers
        string(
            name: 'LUTECE_IMAGE',
            defaultValue: 'nexus-docker-fastdeploy.api.paris.mdp/bild/f98/site-deontologie:1.0.0-SNAPSHOT',
            description: '[TESTCONTAINERS] Image Docker Lutece à tester'
        )

        // Configuration External
        choice(
            name: 'ENVIRONMENT',
            choices: ['recette', 'preprod', 'local'],
            description: '[EXTERNAL] Environnement cible'
        )
        string(
            name: 'LUTECE_URL',
            defaultValue: '',
            description: '[EXTERNAL] URL personnalisée (override ENVIRONMENT)'
        )

        // Suite de tests
        choice(
            name: 'TEST_SUITE',
            choices: [
                'ContainerIntegrationSuite',
                'WorkflowFormsIntegrationSuite',
                'LoginContainerTest',
                'LoginTest',
                'RbacConfigurationTestt',
                'WorkflowCreationTest',
                'FormsCreationTest',
                'FormsSubmissionTest'
            ],
            description: 'Suite de tests à exécuter (ContainerIntegrationSuite pour Docker, WorkflowFormsIntegrationSuite pour externe)'
        )

        // Options
        booleanParam(
            name: 'HEADLESS',
            defaultValue: true,
            description: 'Exécuter en mode headless (sans interface graphique)'
        )

        // SonarQube
        booleanParam(
            name: 'SONAR_ANALYSIS',
            defaultValue: true,
            description: 'Envoyer les rapports à SonarQube'
        )
    }

    environment {
        // Java / Maven
        JAVA_MAVEN = 'temurin-17-jdk'
        MAVEN = 'Maven 3.8.5'
        MAVEN_OPTS = '-Dmaven.repo.local=.m2/repository'

        // Playwright
        PLAYWRIGHT_BROWSERS_PATH = '.playwright-browsers'

        // Container Registry
        DOCKER_REGISTRY = 'nexus-docker-fastdeploy.api.paris.mdp'
        DOCKER_CREDENTIALS_ID = 'NexusSTIPS_bild_user'

        // Lutece Testcontainers config
        LUTECE_CONTEXT_ROOT = '/lutece'
        LUTECE_HTTP_PORT = '9090'

        // Testcontainers avec Podman
        TESTCONTAINERS_RYUK_DISABLED = 'true'
        DOCKER_HOST = 'unix:///run/user/1000/podman/podman.sock'
        TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE = '/run/user/1000/podman/podman.sock'
    }

    options {
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
        disableConcurrentBuilds()
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                echo """
                ╔══════════════════════════════════════════════════════════════╗
                ║              LUTECE E2E TESTS - CONFIGURATION                ║
                ╠══════════════════════════════════════════════════════════════╣
                ║  Mode        : ${params.TEST_MODE}
                ║  Test Suite  : ${params.TEST_SUITE}
                ║  Headless    : ${params.HEADLESS}
                ║  Branch      : ${env.BRANCH_NAME ?: 'N/A'}
                ║  Commit      : ${env.GIT_COMMIT?.take(8) ?: 'N/A'}
                ╚══════════════════════════════════════════════════════════════╝
                """
            }
        }

        stage('Verify Environment') {
            steps {
                echo '=== Vérification de l\'environnement ==='
                withMaven(jdk: "${JAVA_MAVEN}", maven: "${MAVEN}", traceability: false) {
                    sh '''
                        echo "Java:"
                        java -version
                        echo "JAVA_HOME: $JAVA_HOME"

                        echo "Maven:"
                        mvn -version

                        echo "Podman:"
                        podman --version
                        podman info --format '{{.Host.RemoteSocket.Path}}'
                    '''
                }
            }
        }

        stage('Podman Login') {
            when {
                expression { params.TEST_MODE == 'TESTCONTAINERS' }
            }
            steps {
                echo '=== Connexion au registry avec Podman ==='
                withCredentials([usernamePassword(
                    credentialsId: "${DOCKER_CREDENTIALS_ID}",
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'DOCKER_PASS'
                )]) {
                    sh '''
                        echo "$DOCKER_PASS" | podman login -u "$DOCKER_USER" --password-stdin ${DOCKER_REGISTRY}
                    '''
                }
            }
        }

        stage('Pull Lutece Image') {
            when {
                expression { params.TEST_MODE == 'TESTCONTAINERS' }
            }
            steps {
                echo "=== Pull de l'image: ${params.LUTECE_IMAGE} ==="
                sh """
                    podman pull ${params.LUTECE_IMAGE}
                    echo "Image téléchargée:"
                    podman images | grep -E "site-deontologie|lutece" | head -5
                """
            }
        }

        stage('Determine Target URL') {
            when {
                expression { params.TEST_MODE == 'EXTERNAL' }
            }
            steps {
                script {
                    if (params.LUTECE_URL?.trim()) {
                        env.TARGET_URL = params.LUTECE_URL
                    } else {
                        switch(params.ENVIRONMENT) {
                            case 'recette':
                                env.TARGET_URL = 'https://f56-forms-dsin.rec.apps.paris.mdp/lutece'
                                break
                            case 'preprod':
                                env.TARGET_URL = 'https://preprod.lutece.paris.fr/site-deontologie'
                                break
                            case 'local':
                            default:
                                env.TARGET_URL = 'http://localhost:9080/lutece'
                        }
                    }
                    echo "Target URL: ${env.TARGET_URL}"
                }
            }
        }

        stage('Install Dependencies') {
            steps {
                echo '=== Installation des dépendances Maven ==='
                withMaven(jdk: "${JAVA_MAVEN}", maven: "${MAVEN}", traceability: false) {
                    sh 'mvn clean install -DskipTests -B -q'
                }
            }
        }

        stage('Install Playwright Browsers') {
            steps {
                echo '=== Installation des navigateurs Playwright ==='
                withMaven(jdk: "${JAVA_MAVEN}", maven: "${MAVEN}", traceability: false) {
                    sh '''
                        mvn exec:java \
                            -e \
                            -Dexec.mainClass=com.microsoft.playwright.CLI \
                            -Dexec.args="install chromium --with-deps"
                    '''
                }
            }
        }

        stage('Run E2E Tests - Testcontainers') {
            when {
                expression { params.TEST_MODE == 'TESTCONTAINERS' }
            }
            steps {
                echo """
                ╔══════════════════════════════════════════════════════════════╗
                ║           EXÉCUTION DES TESTS TESTCONTAINERS                 ║
                ╠══════════════════════════════════════════════════════════════╣
                ║  Image Lutece : ${params.LUTECE_IMAGE}
                ║  Context Root : ${LUTECE_CONTEXT_ROOT}
                ║  Port HTTP    : ${LUTECE_HTTP_PORT}
                ║  Test Suite   : ${params.TEST_SUITE}
                ╚══════════════════════════════════════════════════════════════╝
                """

                script {
                    def testClass = params.TEST_SUITE
                    // Pour le mode conteneur, mapper les tests externes vers leurs équivalents conteneur
                    if (testClass == 'WorkflowFormsIntegrationSuite') {
                        testClass = 'ContainerIntegrationSuite'
                    }
                    if (testClass == 'LoginTest') {
                        testClass = 'LoginContainerTest'
                    }

                    sh 'mkdir -p target/screenshots'

                    withMaven(jdk: "${JAVA_MAVEN}", maven: "${MAVEN}", traceability: false) {
                        sh """
                            mvn test \
                                -Dtest=${testClass} \
                                -Dlutece.image=${params.LUTECE_IMAGE} \
                                -Dlutece.context.root=${LUTECE_CONTEXT_ROOT} \
                                -Dlutece.http.port=${LUTECE_HTTP_PORT} \
                                -Dtest.headless=${params.HEADLESS} \
                                -Dtest.timeout=30000 \
                                -B \
                                --fail-at-end
                        """
                    }
                }
            }
            post {
                always {
                    junit(
                        testResults: 'target/surefire-reports/*.xml',
                        allowEmptyResults: true
                    )
                }
            }
        }

        stage('Run E2E Tests - External') {
            when {
                expression { params.TEST_MODE == 'EXTERNAL' }
            }
            steps {
                echo """
                ╔══════════════════════════════════════════════════════════════╗
                ║           EXÉCUTION DES TESTS SUR INSTANCE EXTERNE           ║
                ╠══════════════════════════════════════════════════════════════╣
                ║  URL         : ${env.TARGET_URL}
                ║  Test Suite  : ${params.TEST_SUITE}
                ╚══════════════════════════════════════════════════════════════╝
                """

                script {
                    def testClass = params.TEST_SUITE
                    // Pour le mode externe, mapper les tests conteneur vers leurs équivalents externes
                    if (testClass == 'ContainerIntegrationSuite') {
                        testClass = 'WorkflowFormsIntegrationSuite'
                    }
                    if (testClass == 'LoginContainerTest') {
                        testClass = 'LoginTest'
                    }

                    sh 'mkdir -p target/screenshots'

                    withMaven(jdk: "${JAVA_MAVEN}", maven: "${MAVEN}", traceability: false) {
                        sh """
                            mvn test \
                                -Dtest=${testClass} \
                                -Dlutece.base.url=${env.TARGET_URL} \
                                -Dtest.headless=${params.HEADLESS} \
                                -Dtest.timeout=10000 \
                                -B \
                                --fail-at-end
                        """
                    }
                }
            }
            post {
                always {
                    junit(
                        testResults: 'target/surefire-reports/*.xml',
                        allowEmptyResults: true
                    )
                }
            }
        }

        stage('SonarQube Analysis') {
            when {
                expression { params.SONAR_ANALYSIS == true }
            }
            steps {
                echo '''
                ╔══════════════════════════════════════════════════════════════╗
                ║                 ANALYSE SONARQUBE                            ║
                ╚══════════════════════════════════════════════════════════════╝
                '''

                withSonarQubeEnv('SonarQube') {
                    withMaven(jdk: "${JAVA_MAVEN}", maven: "${MAVEN}", traceability: false) {
                        sh """
                            mvn sonar:sonar \
                                -Dsonar.projectKey=lutece-e2e-tests \
                                -Dsonar.projectName='Lutece E2E Tests' \
                                -Dsonar.sources=src/test/java \
                                -Dsonar.tests=src/test/java \
                                -Dsonar.java.binaries=target/test-classes \
                                -Dsonar.junit.reportPaths=target/surefire-reports \
                                -Dsonar.qualitygate.wait=false \
                                -B
                        """
                    }
                }
            }
        }

        stage('Quality Gate') {
            when {
                expression { params.SONAR_ANALYSIS == true }
            }
            steps {
                echo '=== Vérification du Quality Gate SonarQube ==='
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: false
                }
            }
        }
    }

    post {
        always {
            echo '=== Archivage des artefacts ==='

            // Archiver les screenshots
            archiveArtifacts(
                artifacts: 'target/screenshots/**/*.png',
                allowEmptyArchive: true,
                fingerprint: true
            )

            // Archiver les rapports Surefire
            archiveArtifacts(
                artifacts: 'target/surefire-reports/**/*',
                allowEmptyArchive: true
            )

            // Logout Podman (si connecté)
            sh 'podman logout ${DOCKER_REGISTRY} || true'

            // Nettoyage des conteneurs et images Testcontainers orphelins
            sh '''
                echo "=== Nettoyage des conteneurs Testcontainers ==="
                podman container prune -f || true
                podman network prune -f || true
                podman images --filter "dangling=true" --format "{{.ID}}" | xargs -r podman rmi || echo "Aucune image dangling à supprimer."
            '''

            // Nettoyage workspace
            cleanWs(
                cleanWhenNotBuilt: false,
                deleteDirs: true,
                notFailBuild: true,
                patterns: [
                    [pattern: 'target/**', type: 'INCLUDE'],
                    [pattern: '.m2/**', type: 'EXCLUDE'],
                    [pattern: '.playwright-browsers/**', type: 'EXCLUDE']
                ]
            )
        }

        success {
            echo '''
            ╔══════════════════════════════════════════════════════════════╗
            ║                    ✅ TESTS RÉUSSIS                          ║
            ╚══════════════════════════════════════════════════════════════╝
            '''

            // Notification Slack (décommenter si configuré)
            // slackSend(
            //     color: 'good',
            //     message: "✅ Tests E2E réussis - ${env.JOB_NAME} #${env.BUILD_NUMBER}"
            // )
        }

        failure {
            echo '''
            ╔══════════════════════════════════════════════════════════════╗
            ║                    ❌ TESTS ÉCHOUÉS                          ║
            ╚══════════════════════════════════════════════════════════════╝
            '''

            // Archiver les logs des conteneurs en cas d'échec
            script {
                if (params.TEST_MODE == 'TESTCONTAINERS') {
                    sh '''
                        echo "=== Récupération des logs des conteneurs ==="
                        for container in $(podman ps -aq | head -3); do
                            echo "=== Logs du conteneur $container ===" >> target/container-logs.txt
                            podman logs $container >> target/container-logs.txt 2>&1 || true
                        done
                    '''
                    archiveArtifacts(
                        artifacts: 'target/container-logs.txt',
                        allowEmptyArchive: true
                    )
                }
            }

            // Notification Slack (décommenter si configuré)
            // slackSend(
            //     color: 'danger',
            //     message: "❌ Tests E2E échoués - ${env.JOB_NAME} #${env.BUILD_NUMBER}\n${env.BUILD_URL}"
            // )
        }

        unstable {
            echo '''
            ╔══════════════════════════════════════════════════════════════╗
            ║               ⚠️ TESTS INSTABLES (certains échecs)            ║
            ╚══════════════════════════════════════════════════════════════╝
            '''
        }
    }
}
