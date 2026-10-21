# ViciontRP

Plugin para servidores de Minecraft (Paper, Purpur o Spigot) y mod para Fabric que reparten y actualizan los **packs de recursos** de una instancia de [Viciont Studios Launcher](https://viciontstudios.pages.dev/#launcher).

- Quien entra **desde el launcher con la instancia al día** ya tiene los packs: no se le pregunta ni se le descarga nada.
- Quien entra **sin el launcher** (o con la instancia sin actualizar) recibe los packs del servidor al entrar, con tu mensaje. Si son obligatorios, solo puede **aceptarlos o desconectarse**.
- Cuando cambias los packs en el launcher y publicas una versión de la instancia, el plugin lo nota solo y avisa en el chat antes de actualizar a quien ya está jugando:
  - **Con el launcher**: el mod ViciontRP (lo pone el launcher solo) le pide al launcher los packs nuevos. Sale abajo **«Actualizando Assets 100% | (0/3)»** con una barra blanca y, al llenarse, se recargan las texturas. El launcher cambia la carpeta `resourcepacks` de la instancia, así que no se duplica nada y no hace falta reiniciar.
  - **Sin el launcher**: le llega el pack del servidor que cambió.
- El servidor puede ser **solo con el launcher**: a quien entre sin él se le echa con tu mensaje.

## Requisitos

- Paper, Purpur o Spigot **1.18 o más nuevo** (probado en 1.21.4 y 26.3). Para mandar **varios packs** y actualizar a quien ya está jugando hace falta la 1.20.3 o más nueva.
- Una instancia publicada con Viciont Studios Launcher (1.5.2 o más nuevo) y el permiso **Editar y publicar** en ella.
- Para actualizar sin salir del servidor desde el launcher: instancia **Fabric o Quilt** de Minecraft **1.21, 1.21.1, 1.21.11 o 26.2** (las demás versiones llegarán más adelante). El launcher pone el mod ViciontRP él solo.

## Instalación

1. Descarga `ViciontRP-x.y.z.jar` de [Releases](https://github.com/CrissyjuanxD/ViciontRP/releases/latest) y ponlo en la carpeta `plugins` del servidor.
2. Arranca el servidor una vez: se crea `plugins/ViciontRP/config.yml`.
3. En el launcher: **Administrar instancias → tu instancia → Contenido → Pack del servidor de Minecraft**.
   - Marca los packs (hasta 8). Pueden ser `.zip` o carpetas de `resourcepacks`: de una carpeta, el launcher crea el `.zip` al publicar.
   - Publica la versión (o pulsa **Guardar solo textos y permisos**).
   - Pulsa **Crear clave** y copia la línea `key: "…"`.
4. Pega esa línea en `config.yml` y escribe `/viciontrp reload`.

## Configuración

| Opción | Qué hace |
| --- | --- |
| `key` | Clave del plugin creada en el launcher. |
| `api` | Servidor de Viciont Studios. No hay que cambiarlo. |
| `launcher-link` | Enlace que sale en los mensajes como `{link}`. |
| `access` | `everyone`: con o sin el launcher. `launcher-only`: solo desde el launcher; a los demás se les echa con el mensaje `launcher-only`. |
| `update-mode` | `automatic`: se actualiza a quien ya juega en cuanto publicas. `manual`: solo con `/viciontrp actualizar`. |
| `warning-seconds` | Segundos de aviso en el chat antes de actualizar (0 = sin aviso). |
| `required` | `true`: los packs son obligatorios y el jugador solo puede aceptarlos o desconectarse. |
| `refresh-seconds` | Cada cuántos segundos mira si hay recursos nuevos (mínimo 15). |
| `join-delay-ticks` | Espera entre que el jugador entra y se le mandan los packs (20 ticks = 1 segundo). |

En modo manual, quien entra nuevo recibe igualmente los recursos de la última versión publicada: el modo solo decide cuándo se actualiza a quien **ya está jugando**. Así puedes, por ejemplo, poner una cuenta atrás con otro plugin y, al acabar, escribir `/viciontrp actualizar`.

## Mensajes

En `config.yml`, apartado `messages`, con colores `&` (`&6`, `&l`…) o **hexadecimales** con `&#RRGGBB` (por ejemplo `&#C8A2F8` morado pastel, `&#F8B4D9` rosa pastel, `&#A7D8F5` celeste pastel). Se pueden usar varias líneas.

| Mensaje | Cuándo sale |
| --- | --- |
| `prompt` | En la pantalla del pack, encima de los botones. |
| `declined` | Al echar a quien no lo acepta (con `required: true`). |
| `failed` | Al echar a quien no pudo descargarlo o cargarlo. |
| `chat` | En el chat, cuando ya lo tiene cargado. Vacío (`""`) para no mandarlo. |
| `warning` | En el chat, antes de actualizar los recursos. `{seconds}` = segundos que faltan. |
| `launcher-only` | Al echar a quien no entra desde el launcher (con `access: launcher-only`). |
| `pending` | A los administradores, cuando hay recursos nuevos en modo manual. |

Se pueden usar `{instance}` (nombre de la instancia), `{link}` (`launcher-link`) y `{player}` (nick).

## Comandos y permisos

- `/viciontrp status`: instancia, packs, modo, acceso y si hay algún problema con la clave o la conexión.
- `/viciontrp actualizar`: manda los recursos nuevos a quien está jugando, después del aviso. `/viciontrp actualizar ahora`, sin aviso.
- `/viciontrp reload`: vuelve a leer `config.yml`.
- `viciontrp.admin` (por defecto, operadores): usar `/viciontrp`.
- `viciontrp.bypass`: nunca recibe los packs ni necesita el launcher (por ejemplo, para el staff).

## Cómo sabe quién viene del launcher

Mientras alguien juega, el launcher le dice al servidor de Viciont Studios qué instancia está jugando y qué packs tiene instalados. Al entrar un jugador, el plugin pregunta por su nick usando la clave de la instancia (sin la clave nadie puede preguntarlo). Con el mod, además, el launcher le da al juego un **pase de esa partida** que el plugin comprueba con el servidor de Viciont Studios: así `launcher-only` no se puede engañar poniéndose el nick de otro.

Si no se puede conectar con el servidor de Viciont Studios, el plugin manda los últimos packs conocidos a todos y no echa a nadie por `launcher-only`, para que una caída no deje a todos fuera.

## El mod

El mod ViciontRP es solo de cliente y no hace nada sin el plugin. Habla con el plugin por el canal `viciontrp:main` y con el launcher por un canal privado del propio PC (`127.0.0.1`, con una clave distinta en cada partida). Los archivos de la carpeta `resourcepacks` los cambia siempre el launcher, en el momento de la recarga en que el juego ya ha soltado los packs (por eso funciona también con `.zip` en Windows).

Hay un `.jar` por grupo de versiones de Minecraft (por ahora 1.21–1.21.1, 1.21.11 y 26.2; el resto está en `mod/versions/` con `release=false` hasta probarlo en el juego) y el launcher elige el correcto con el `mod.json` de cada release. Si alguien lo instala sin el launcher, solo acepta los packs del servidor sin preguntar.

## Compilar

```
cd plugin && mvn package
cd mod && ./gradlew -Ptarget=1.21.1 build
```

El plugin queda en `plugin/target/` y el mod en `mod/build/libs/` (los objetivos están en `mod/versions/`). Cada versión se publica sola al subir una etiqueta `vX.Y.Z` (con sus notas en `docs/releases/X.Y.Z.md`): el plugin, los `.jar` del mod y `mod.json`.
