-- Le nom d'un bureau de douane (texte libre côté frontend) dépassait souvent
-- la limite de 20 caractères, provoquant une erreur 500 non gérée à la création
-- d'une déclaration douanière.
ALTER TABLE customs_declarations ALTER COLUMN customs_office TYPE VARCHAR(150);
