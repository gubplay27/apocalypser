# Apocalypse Plus (Forge 1.20.1)

Mod para apocalipsis zombi: hordas que crecen con los días, zombis especiales, ruido que atrae
zombis, eventos aleatorios y niebla constante (solo cliente).

## Compilar con GitHub (sin instalar nada)

1. Crea un repositorio nuevo en github.com.
2. Sube TODO el contenido de esta carpeta (Add file > Upload files): `src`, `build.gradle`,
   `settings.gradle` y `gradle.properties`.
3. Crea el archivo `.github/workflows/build.yml` con "Add file > Create new file" y pega el contenido
   de `.github/workflows/build.yml` de este proyecto.
4. Ve a la pestaña Actions, espera el check verde y descarga el jar en "Artifacts".

## Configuración

- `config/apocalypse_plus-common.toml`: hordas, zombis especiales, ruido y eventos
- `config/apocalypse_plus-client.toml`: niebla (poner `nieblaActivada = false` para quitarla)

## Notas

- Los disparos se detectan por el nombre del sonido de otros mods (palabras en `palabrasDeDisparo`).
- Con shaders activados la niebla la controla el shader.
