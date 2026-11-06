# KAYRA Android Assistant

KAYRA is a Turkish-language Android voice assistant prototype built with Kotlin and Jetpack Compose.

## Included in this version
- Jarvis-inspired dark blue/cyan dashboard
- Turkish speech recognition using Android's speech recognition service
- Turkish text-to-speech using the device's installed voice
- Gemini responses through the configured Cloudflare Worker
- On-device saved facts and the latest 50 conversation pairs
- Voice commands to save and remove facts

## Build APK with GitHub Actions
1. Upload the contents of this project to the `main` branch of your GitHub repository.
2. Open **Actions** and run **KAYRA Android APK** (or push a commit to `main`).
3. Open the successful workflow run and download the `KAYRA-APK` artifact.

## Important limitations
- The wake phrase "Hey Kayra" while the app is closed or the screen is off is not implemented in this build.
- Phone actions such as placing calls, sending messages, changing settings, and controlling other apps are not implemented yet.
- Speech recognition availability and offline operation depend on the speech service and language packs installed on the phone.
- Gemini replies require an internet connection and a working Cloudflare Worker with the `GEMINI_API_KEY` secret configured.
- Saved facts and conversation history are stored locally on the device. Only the latest 10 conversation pairs are added to an AI request.

## Example voice commands
- `En sevdiğim renk mavi, hatırla.`
- `Hatırla: En sevdiğim renk mavi.`
- `Hafızanda ne var?`
- `Unut: En sevdiğim renk mavi.`

The Gemini API key must remain in the Cloudflare Worker secret. Do not place it in the Android app or commit it to GitHub.


## Hey Kayra uyandırma modu

Uygulama içindeki **HEY KAYRA MODUNU AÇ** düğmesi, mikrofon kullanan görünür bir Android ön plan servisini başlatır. Servis bildirimde çalıştığını gösterir; "Hey Kayra" ifadesini algıladığında komut dinler ve yanıtı seslendirir.

Önemli sınırlamalar:
- Bu sürüm, Android `SpeechRecognizer` hizmetini tekrar başlatarak uyandırma kelimesini bekler. Özel bir düşük güç hotword motoru değildir.
- Ekran kapalıyken çalışma, pil tüketimi ve tanıma hizmetinin davranışı telefon modeline / Android sürümüne göre değişebilir.
- Çevrimdışı tanıma yalnızca telefonda Türkçe konuşma modeli ve destekleyen tanıma hizmeti varsa mümkündür. Gemini yanıtları için internet gerekir.
- Servisi uygulamadaki aynı düğmeden kapatabilirsin. Mikrofon kullanımı görünür Android bildirimiyle belirtilir.
