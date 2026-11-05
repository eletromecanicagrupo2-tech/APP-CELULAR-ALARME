# 🚽 Alarme Vaso — Alarme com Missão Fotográfica (PT-BR)

Aplicativo Android de alarme em **português brasileiro**, sincronizado com o
**fuso horário de Brasília (America/Sao_Paulo)**, com seleção de músicas via
**Spotify** e uma missão inusitada: para o alarme parar, você precisa
**levantar da cama e fotografar um vaso sanitário!** 📸🚽

## ✨ Funcionalidades

- ⏰ Alarme com fuso horário de Brasília (horário 24h, PT-BR)
- 🎵 Seleção de músicas com integração/deep link com o Spotify
- 📸 Missão anti-soneca: o alarme só para após tirar uma foto de um vaso sanitário
- 🔔 Notificações, vibração e tela cheia ao disparar (bypass de lockscreen)
- ♻️ Religa os alarmes automaticamente após reiniciar o celular
- 🧪 Botão "Testar agora" para experimentar a missão sem esperar o horário

## 📱 Como instalar

1. Baixe o arquivo **`AlarmeVaso-debug.apk`** na seção *Releases* deste repositório
   (ou diretamente do repositório).
2. Transfira para o celular (cabo, Drive, WhatsApp etc.).
3. Toque no arquivo e permita a instalação de **"apps de origem desconhecida"** quando solicitado.
4. Abra o app e conceda as permissões: **Notificações**, **Câmera** e **Alarms & Reminders**.

> Compatível com **Android 7.0+ (API 24)** até Android 14.

## 🛠️ Compilando do zero

```bash
cd AlarmeVaso
./gradlew assembleDebug
# APK gerado em: AlarmeVaso/app/build/outputs/apk/debug/app-debug.apk
```

Pré-requisitos: JDK 17 e Android SDK (platform 34 + build-tools 34.0.0).

## 📂 Estrutura

```
AlarmeVaso/           # Projeto Android (Kotlin)
  app/src/main/java/com/example/alarmevaso/
    MainActivity.kt        # Tela principal: horário, músicas, ativação
    AlarmActivity.kt       # Tela do alarme + missão da foto (câmera)
    AlarmScheduler.kt      # Agendamento (AlarmManager, fuso America/Sao_Paulo)
    AlarmReceiver.kt       # Receiver que dispara o alarme
    AlarmSoundService.kt   # Serviço de som/vibração
    BootReceiver.kt        # Religa alarmes após reboot
    SpotifyMusic.kt        # Catálogo de músicas + deep links do Spotify
    AppPrefs.kt / AlarmApp.kt
AlarmeVaso-debug.apk  # APK pronto para instalar (debug)
```

## ⚠️ Observações

- Este é um APK **debug** (assinado com chave de debug), ideal para teste pessoal.
  Para publicar na Play Store, gere um build *release* assinado.
- A seleção de músicas abre o Spotify no celular; se o app não estiver instalado,
  o alarme usa os sons locais incluídos (`assets/music`).

---

Feito com ☕ e 🚽 — levante da cama e fotografe o vaso!
