#!/bin/bash
#
# Script pour lancer les tests E2E en local
#
# Usage:
#   ./run-e2e-tests.sh                    # Tests contre localhost:9080
#   ./run-e2e-tests.sh remote             # Tests contre la recette
#   ./run-e2e-tests.sh docker             # Tests avec Docker Compose
#   ./run-e2e-tests.sh <url>              # Tests contre une URL specifique
#

set -e

# Couleurs pour l'affichage
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration par defaut
DEFAULT_URL="http://localhost:9080/site-deontologie"
RECETTE_URL="https://f56-forms-dsin.rec.apps.paris.mdp/lutece"
HEADLESS="${HEADLESS:-true}"

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Determiner l'URL cible
case "${1:-local}" in
    remote|recette)
        TARGET_URL="$RECETTE_URL"
        log_info "Mode: Tests contre l'environnement de recette"
        ;;
    docker)
        log_info "Mode: Tests avec Docker Compose"
        TARGET_URL="http://localhost:9080/site-deontologie"

        # Verifier que Docker est disponible
        if ! command -v docker-compose &> /dev/null; then
            log_error "docker-compose n'est pas installe"
            exit 1
        fi

        # Demarrer l'environnement Docker
        log_info "Demarrage de l'environnement Docker..."
        docker-compose -f docker-compose.e2e.yml up -d db lutece

        # Attendre que Lutece soit pret
        log_info "Attente du demarrage de Lutece..."
        for i in $(seq 1 60); do
            if curl -s "$TARGET_URL/" > /dev/null 2>&1; then
                log_info "Lutece est pret!"
                break
            fi
            echo -n "."
            sleep 5
        done
        echo ""
        ;;
    local)
        TARGET_URL="$DEFAULT_URL"
        log_info "Mode: Tests contre localhost"
        ;;
    http*|https*)
        TARGET_URL="$1"
        log_info "Mode: Tests contre URL personnalisee"
        ;;
    *)
        log_error "Usage: $0 [local|remote|docker|<url>]"
        exit 1
        ;;
esac

log_info "URL cible: $TARGET_URL"
log_info "Mode headless: $HEADLESS"

# Verifier que Maven est installe
if ! command -v mvn &> /dev/null; then
    log_error "Maven n'est pas installe"
    exit 1
fi

# Installer les navigateurs Playwright si necessaire
log_info "Verification des navigateurs Playwright..."
mvn exec:java -e -q \
    -Dexec.mainClass=com.microsoft.playwright.CLI \
    -Dexec.args="install chromium" \
    2>/dev/null || true

# Lancer les tests
log_info "Lancement des tests E2E..."
mvn test -Dtest=WorkflowFormsIntegrationSuite \
    -Dlutece.base.url="$TARGET_URL" \
    -Dtest.headless="$HEADLESS"

TEST_RESULT=$?

# Nettoyer Docker si necessaire
if [ "${1:-local}" = "docker" ]; then
    log_info "Arret de l'environnement Docker..."
    docker-compose -f docker-compose.e2e.yml down -v
fi

# Afficher le resultat
echo ""
if [ $TEST_RESULT -eq 0 ]; then
    log_info "============================================"
    log_info "  TOUS LES TESTS E2E SONT PASSES"
    log_info "============================================"
else
    log_error "============================================"
    log_error "  DES TESTS E2E ONT ECHOUE"
    log_error "============================================"
    log_info "Voir les rapports dans: target/surefire-reports/"
    log_info "Captures d'ecran dans: target/screenshots/"
fi

exit $TEST_RESULT
