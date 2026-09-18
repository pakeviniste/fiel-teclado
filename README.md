# Fiel — teclado de voz

Fiel no es una app aislada. Es un **teclado del sistema**. Vive junto a tus otros teclados. El texto entra en WhatsApp, Notas, el correo — donde esté el cursor.

- Android: IME nativo (`InputMethodService`). APK por sideload, temporal, gratis.
- iPhone: en camino. Hoy solo Android.

## Android (el camino que funciona hoy)

1. Instala `fiel-teclado.apk` (Actions → artifacts, o compílalo).
2. Abre el enlace de emparejado que te da la web (`fiel://pair`).
3. Permite el micrófono.
4. Ajustes del teléfono → **Sistema → Idiomas e introducción → Teclado virtual → Administrar teclados** → activa **Fiel**.
5. En cualquier caja de texto, toca el **globo** y elige Fiel.
6. Mantén el círculo y habla. Suelta. El texto se escribe en esa caja.

```bash
cd android
# sdk.dir en local.properties
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

El APK es de depuración a propósito: se instala sin cuenta de Play. Android avisará «origen desconocido»; es normal. Sideload temporal, no el canal de producto.

## iPhone

En camino. Hoy solo Android.

Quien desarrolle la extensión: el código está en `ios/` (`UIInputViewController`, acceso total para red + micro).

## Cómo habla con la web

El teclado envía el WAV a:

```
POST {api}/api/keyboard-transcribe
Authorization: Bearer {token}
Content-Type: multipart/form-data
file=speech.wav
language=es|ca|en
```

La clave se genera en la web de Fiel (cuenta). El diccionario de esa cuenta se aplica al transcribir.

## Licencia

MIT. Úsalo, fórkealo, súbelo a F-Droid si quieres.
