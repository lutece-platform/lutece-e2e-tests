-- Script d'initialisation de la base de données Lutece pour les tests E2E
-- Ce fichier est optionnel si votre image Docker contient déjà la base initialisée.

-- Création du schéma de base (si nécessaire)
-- Ajoutez ici les tables et données initiales requises pour vos tests.

-- Exemple: créer l'utilisateur admin si pas présent
-- INSERT IGNORE INTO core_admin_user (id_user, access_code, last_name, first_name, email, status, password, locale)
-- VALUES (1, 'admin', 'Admin', 'Admin', 'admin@lutece.fr', 1, 'PBKDF2WITHHMACSHA512:...', 'fr');

-- Note: Si votre image rafikyahiaoui/lutece-site-8 contient déjà la base initialisée,
-- ce fichier peut rester vide ou être utilisé pour des données de test spécifiques.

SELECT 'Base de données prête pour les tests E2E' AS status;
