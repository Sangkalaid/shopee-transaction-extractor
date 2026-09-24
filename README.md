# Shopee Transaction Extractor

Android native app untuk membaca transaksi yang sedang tampil di halaman Shopee > Riwayat Transaksi menggunakan Android Accessibility API saat capture aktif.

## Teknologi

- Kotlin
- Jetpack Compose
- Material 3
- AccessibilityService
- WindowManager overlay
- Room Database
- DataStore-ready architecture
- Coroutines / Flow

## Cara Build

```bash
./gradlew test
./gradlew assembleDebug
```

APK debug berada di:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Cara Pakai

1. Buka aplikasi.
2. Aktifkan Accessibility Service.
3. Aktifkan izin Display over other apps.
4. Tekan `MULAI`.
5. Buka Shopee.
6. Masuk ke `Riwayat Transaksi`.
7. Tekan floating button.
8. Tekan `START`.
9. Scroll manual.
10. Tekan `BERHENTI`.
11. Buka aplikasi dan export/simpan CSV dari session tersimpan.

## Privasi

Aplikasi bekerja lokal. Aplikasi tidak meminta username, password, PIN, OTP, SMS, kontak, call log, clipboard, screenshot, rekaman layar, atau upload data ke server.

## Catatan Pengujian Perangkat Nyata

Unit test memvalidasi parser nominal, tanggal, transaksi, dan deduplication window. Struktur Accessibility aplikasi Shopee nyata bisa berbeda antar versi/perangkat, sehingga APK tetap perlu diuji pada perangkat Android nyata dengan versi Shopee yang digunakan.

Jika struktur node Shopee tidak cocok, perbaikan harus dilakukan pada parser Accessibility. Jangan mengganti metode utama menjadi screenshot/OCR.
