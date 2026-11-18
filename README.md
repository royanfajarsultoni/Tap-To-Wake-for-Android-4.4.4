# Tap to Wake (Redmi 2 / Android 4.4 KitKat)

Pengganti tombol Power & Volume yang rusak.

| Kebutuhan | Cara |
|---|---|
| Menyalakan layar | Ketuk 2x pada HP (accelerometer) **atau** lambai 2x di atas sensor jarak |
| Mematikan layar | Tombol melayang **OFF** / aksi **Kunci** di notifikasi (Device Admin) |
| Volume naik/turun | Tombol melayang **V+ / V-** / aksi **Vol +/-** di notifikasi |

## Cara membuat APK

### A. Tanpa Android Studio (GitHub Actions)
1. Buat repo GitHub baru (boleh private), upload seluruh isi folder ini (termasuk `.github`).
2. Tab **Actions** > workflow **Build APK** > tunggu hijau (±3-5 menit).
3. Buka run tersebut, unduh artifact **TapToWake-debug-apk** (isinya `app-debug.apk`).

### B. Android Studio
Buka folder ini, tunggu sync, lalu **Build > Build APK(s)**.

## Pemasangan di HP
1. Pengaturan > Keamanan > izinkan **Sumber tidak dikenal**, lalu pasang APK.
2. Buka **Tap to Wake**, tekan **Aktifkan Device Admin** (hanya izin kunci layar).
3. MIUI: izinkan **Autostart**, kunci kartu aplikasi di Recent Apps, kecualikan dari penghemat baterai.
4. Pakai bagian **Uji ketukan** untuk menyetel sensitivitas.

## Cara kerja (singkat)
- Layar mati -> layanan menahan CPU tetap hidup + mendengarkan sensor.
- Layar hidup -> semua sensor dilepas (hemat baterai).
- Ketukan = lonjakan accelerometer; dua lonjakan berjarak 100-500 ms = ketuk ganda.
- Sensor jarak tertutup (saku / menghadap meja) -> ketukan diabaikan (bisa dimatikan).
