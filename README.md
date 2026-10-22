# ZodiacGuard

App Android per registrare entrate e uscite di lavoro, note giornaliere e attivita,
con riepiloghi settimanali e mensili, esportazione PDF, backup e marcatura rapida NFC.

## Build

Servono JDK 17 e Android SDK Platform 34 con Build Tools 34.0.0.

```powershell
.\gradlew.bat build
```

L'APK debug viene generato in `app/build/outputs/apk/debug/app-debug.apk`.
Il workflow GitHub Actions installa automaticamente i componenti SDK richiesti
e pubblica l'APK come artifact.

## Aggiornamenti in-app

All'avvio l'app controlla giornalmente le GitHub Releases e, se trova una
versione più recente, chiede se scaricarla. L'installazione viene poi confermata
nel programma di installazione Android. I dati restano sul dispositivo quando
l'APK è firmato con la stessa chiave; Android non consente di aggiornare
un'installazione firmata con una chiave diversa.

Per abilitare le release:

1. Generare una chiave di firma privata e conservarne una copia di backup:
   `keytool -genkeypair -v -keystore zodiacguard-release.jks -alias zodiacguard -keyalg RSA -keysize 2048 -validity 10000`.
2. Aggiungere nelle impostazioni GitHub del repository questi Actions secrets:
   `ANDROID_SIGNING_KEYSTORE_BASE64`, `ANDROID_KEY_ALIAS`,
   `ANDROID_KEY_PASSWORD` e `ANDROID_STORE_PASSWORD`.
   Su PowerShell, il valore Base64 del keystore si ottiene con
   `[Convert]::ToBase64String([IO.File]::ReadAllBytes(".\zodiacguard-release.jks"))`.
3. Pubblicare un tag di versione crescente, per esempio `v1.0.1`. Il workflow
   `Publish Zodiac Guard release` firma l'APK e lo allega alla release; l'app lo
   individua tramite l'API pubblica di GitHub.

Non committare mai il keystore o le password. Gli APK debug prodotti dal normale
workflow Actions non sono il canale di aggiornamento: le release devono essere
firmate dalla chiave stabile configurata nei secrets.
