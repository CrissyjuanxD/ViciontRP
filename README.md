# ViciontRP

Plugin para servidores de Minecraft (Paper, Purpur o Spigot) que reparte el **pack de recursos** de una instancia de [Viciont Studios Launcher](https://viciontstudios.pages.dev/#launcher).

- Quien entra **desde el launcher con la instancia al día** ya tiene el pack: no se le pregunta ni se le descarga nada.
- Quien entra **sin el launcher** (o con la instancia sin actualizar) recibe el pack al entrar, con tu mensaje. Si el pack es obligatorio, solo puede **aceptarlo o desconectarse**.
- Cuando cambias el pack en el launcher y publicas una versión de la instancia, el plugin **recibe el pack nuevo solo** en menos de un minuto. No hay que tocar `server.properties` ni subir el pack a ningún sitio.

## Requisitos

- Paper, Purpur o Spigot **1.18 o más nuevo** (probado en 1.21.4 y 26.2). Desde la 1.20.3 el pack nuevo también llega a quien ya está jugando.
- Una instancia publicada con Viciont Studios Launcher y el permiso **Editar y publicar** en ella.

## Instalación

1. Descarga `ViciontRP-x.y.z.jar` de [Releases](https://github.com/CrissyjuanxD/ViciontRP/releases/latest) y ponlo en la carpeta `plugins` del servidor.
2. Arranca el servidor una vez: se crea `plugins/ViciontRP/config.yml`.
3. En el launcher: **Administrar instancias → tu instancia → Contenido → Pack del servidor de Minecraft**.
   - Elige el pack. Puede ser un `.zip` o una carpeta de `resourcepacks`: si es una carpeta, el launcher crea el `.zip` al publicar.
   - Publica la versión (o pulsa **Guardar solo textos y permisos**).
   - Pulsa **Crear clave** y copia la línea `key: "…"`.
4. Pega esa línea en `config.yml` y escribe `/viciontrp reload`.

## Configuración

| Opción | Qué hace |
| --- | --- |
| `key` | Clave del plugin creada en el launcher. |
| `api` | Servidor de Viciont Studios. No hay que cambiarlo. |
| `launcher-link` | Enlace que sale en los mensajes como `{link}`. |
| `required` | `true`: el pack es obligatorio y el jugador solo puede aceptarlo o desconectarse. |
| `update-online-players` | Si el pack cambia mientras alguien juega sin el launcher, se lo manda al momento. |
| `refresh-seconds` | Cada cuántos segundos mira si hay un pack nuevo (mínimo 15). |
| `join-delay-ticks` | Espera entre que el jugador entra y se le manda el pack (20 ticks = 1 segundo). |

## Mensajes

En `config.yml`, apartado `messages`, con colores `&` y varias líneas:

| Mensaje | Cuándo sale |
| --- | --- |
| `prompt` | En la pantalla del pack, encima de los botones. |
| `declined` | Al echar a quien no lo acepta (con `required: true`). |
| `failed` | Al echar a quien no pudo descargarlo o cargarlo. |
| `chat` | En el chat, cuando ya lo tiene cargado. Vacío (`""`) para no mandarlo. |

Se pueden usar `{instance}` (nombre de la instancia), `{link}` (`launcher-link`) y `{player}` (nick).

## Comandos y permisos

- `/viciontrp status`: instancia, pack actual y si hay algún problema con la clave o la conexión.
- `/viciontrp reload`: vuelve a leer `config.yml`.
- `viciontrp.admin` (por defecto, operadores): usar `/viciontrp`.
- `viciontrp.bypass`: nunca recibe el pack (por ejemplo, para el staff).

## Cómo sabe quién viene del launcher

Mientras alguien juega, el launcher le dice al servidor de Viciont Studios qué instancia está jugando y qué pack tiene instalado. Al entrar un jugador, el plugin pregunta por su nick usando la clave de la instancia (sin la clave nadie puede preguntarlo). Si no se puede conectar con el servidor de Viciont Studios, manda el último pack conocido a todos, para que a nadie le falte.

En servidores no premium (`online-mode=false`) se mira solo el nick: si alguien se pone el nick de otro que está jugando desde el launcher, lo único que consigue es quedarse sin el pack.

## Compilar

```
mvn package
```

El `.jar` queda en `target/`. Cada versión se publica sola al subir una etiqueta `vX.Y.Z` (con sus notas en `docs/releases/X.Y.Z.md`).
