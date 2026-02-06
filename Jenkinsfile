/**
 * Pipeline Jenkins pour les tests E2E Lutece
 *
 * Deux modes d'exécution:
 * - EXTERNAL: Tests sur une instance Lutece existante (recette, preprod, etc.)
 * - TESTCONTAINERS: Tests avec environnement Podman isolé (MariaDB + Lutece)
 *
 * Architecture:
 * - Étapes Maven: agent any (contrôleur Jenkins)
 * - Étapes Podman/Tests: agent podman
 */

pipeline {
    agent none  // Pas d'agent global, défini par stage

    environment {
        // Java / Maven
        JAVA_MAVEN = 'temurin-17-jdk'
        MAVEN = 'Maven 3.8.5'
        MAVEN_OPTS = '-Dmaven.repo.local=.m2/repository'
        MAVEN_USER_HOME = '.m2'  // Pour que le wrapper télécharge Maven dans le workspace

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

    parameters {
        choice(
            name: 'TEST_MODE',
            choices: ['TESTCONTAINERS', 'EXTERNAL'],
            description: 'Mode d\'exécution: TESTCONTAINERS (Docker isolé) ou EXTERNAL (instance existante)'
        )
        string(
            name: 'LUTECE_IMAGE',
            defaultValue: 'nexus-docker-fastdeploy.api.paris.mdp/bild/f98/site-deontologie:1.0.0-SNAPSHOT',
            description: '[TESTCONTAINERS] Image Docker Lutece à tester'
        )
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
            description: 'Suite de tests à exécuter'
        )
        booleanParam(
            name: 'HEADLESS',
            defaultValue: true,
            description: 'Exécuter en mode headless'
        )
        booleanParam(
            name: 'SONAR_ANALYSIS',
            defaultValue: false,
            description: 'Envoyer les rapports à SonarQube'
        )
    }

    options {
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
        disableConcurrentBuilds()
    }

    stages {
        stage('Build & Prepare') {
            agent any  // Contrôleur Jenkins avec Maven
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
                                echo "Maven:"
                                mvn -version
                            '''
                        }
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
                            sh '''
                                # Forcer le local repository dans le workspace pour le stash
                                mvn clean install -DskipTests -Dmaven.repo.local=.m2/repository -B -q
                                # Télécharger TOUT pour le mode offline
                                mvn dependency:go-offline -Dmaven.repo.local=.m2/repository -B
                                # Forcer le téléchargement explicite des plugins de test
                                mvn surefire:help failsafe:help -Dmaven.repo.local=.m2/repository -B -q
                                # Initialiser le Maven Wrapper
                                ./mvnw -Dmaven.repo.local=.m2/repository --version
                            '''
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
                                    -Dexec.args="install chromium"
                            '''
                        }
                    }
                }

                stage('Stash Artifacts') {
                    steps {
                        echo '=== Préparation des artefacts pour l\'agent Podman ==='
                        // Inclure le workspace, les navigateurs Playwright et le repo Maven local
                        // useDefaultExcludes: false pour inclure les répertoires cachés (.m2, .mvn, etc.)
                        stash includes: '**', name: 'workspace-stash', useDefaultExcludes: false
                    }
                }
            }
        }

        stage('Run Tests on Podman Agent') {
            agent { label 'podman' }
            stages {
                stage('Unstash Workspace') {
                    steps {
                        unstash 'workspace-stash'
                    }
                }

                stage('Verify Podman') {
                    steps {
                        echo '=== Vérification de Podman ==='
                        sh '''
                            echo "Podman:"
                            podman --version
                            podman info --format '{{.Host.RemoteSocket.Path}}'
                        '''
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
                            if (testClass == 'WorkflowFormsIntegrationSuite') {
                                testClass = 'ContainerIntegrationSuite'
                            }
                            if (testClass == 'LoginTest') {
                                testClass = 'LoginContainerTest'
                            }

                            sh 'mkdir -p target/screenshots'

                            // Utiliser JAVA_HOME dérivé du binaire java sur l'agent podman
                            sh """
                                export JAVA_HOME=\$(dirname \$(dirname \$(readlink -f \$(which java))))
                                ./mvnw test -o \
                                    -Dmaven.repo.local=.m2/repository \
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
                            if (testClass == 'ContainerIntegrationSuite') {
                                testClass = 'WorkflowFormsIntegrationSuite'
                            }
                            if (testClass == 'LoginContainerTest') {
                                testClass = 'LoginTest'
                            }

                            sh 'mkdir -p target/screenshots'

                            sh """
                                export JAVA_HOME=\$(dirname \$(dirname \$(readlink -f \$(which java))))
                                ./mvnw test -o \
                                    -Dmaven.repo.local=.m2/repository \
                                    -Dtest=${testClass} \
                                    -Dlutece.base.url=${env.TARGET_URL} \
                                    -Dtest.headless=${params.HEADLESS} \
                                    -Dtest.timeout=10000 \
                                    -B \
                                    --fail-at-end
                            """
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

                stage('Cleanup Podman') {
                    steps {
                        echo '=== Nettoyage Podman ==='
                        sh '''
                            podman logout ${DOCKER_REGISTRY} || true
                            podman container prune -f || true
                            podman network prune -f || true
                            podman images --filter "dangling=true" --format "{{.ID}}" | xargs -r podman rmi || echo "Aucune image dangling à supprimer."
                        '''
                    }
                }

                stage('Archive Results') {
                    steps {
                        echo '=== Archivage des artefacts ==='
                        archiveArtifacts(
                            artifacts: 'target/screenshots/**/*.png',
                            allowEmptyArchive: true,
                            fingerprint: true
                        )
                        archiveArtifacts(
                            artifacts: 'target/surefire-reports/**/*',
                            allowEmptyArchive: true
                        )
                    }
                }
            }
        }

        stage('SonarQube Analysis') {
            agent any
            when {
                expression { params.SONAR_ANALYSIS == true }
            }
            steps {
                unstash 'workspace-stash'
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
    }

    post {
        success {
            echo '''
            ╔══════════════════════════════════════════════════════════════╗
            ║                    ✅ TESTS RÉUSSIS                          ║
            ╚══════════════════════════════════════════════════════════════╝
            '''
        }
        failure {
            echo '''
            ╔══════════════════════════════════════════════════════════════╗
            ║                    ❌ TESTS ÉCHOUÉS                          ║
            ╚══════════════════════════════════════════════════════════════╝
            '''
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
