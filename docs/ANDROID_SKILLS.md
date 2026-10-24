# Android Skills untuk Dislauncher

> Tinjauan: [Android Skills v1.0.16](https://github.com/android/skills/releases/tag/v1.0.16)  
> Sumber: [android/skills](https://github.com/android/skills) · [README upstream](https://github.com/android/skills/blob/v1.0.16/README.md)

## Kesimpulan

Android Skills berguna sebagai referensi kerja untuk AI coding assistant ketika mengubah atau meninjau kode Android. Isinya berupa instruksi modular `SKILL.md` dan referensi teknis yang ditujukan untuk kasus-kasus Android tertentu—bukan library runtime yang perlu ditambahkan ke APK Dislauncher.

Untuk Dislauncher, manfaat paling nyata adalah memperkuat strategi pengujian, pemeriksaan performa, dan peninjauan izin/perilaku sistem. Dokumentasi ini mencatat cara memakainya tanpa menambah dependensi aplikasi atau mengubah konfigurasi build.

## Skill yang paling relevan

| Prioritas | Skill upstream | Kegunaan untuk Dislauncher | Kapan digunakan |
|---|---|---|---|
| Tinggi | [testing-setup](https://github.com/android/skills/tree/v1.0.16/testing/testing-setup) | Menyusun strategi unit test, UI test, screenshot/regression test, dan pengujian end-to-end. | Saat mengubah pencarian app drawer, favorit/hidden apps, pengaturan, reminder, atau komponen Compose. |
| Tinggi, saat ada masalah performa | [perfetto-trace-analysis](https://github.com/android/skills/tree/v1.0.16/profilers/android-profiler/analysis/workflows/perfetto-trace-analysis) | Membantu menganalisis trace untuk mencari jank, keterlambatan, dan bottleneck. | Jika Home screen atau app drawer terasa lambat, scrolling tersendat, atau startup memburuk. Memerlukan trace/perangkat atau artefak profiling; tidak otomatis berjalan di GitHub Actions biasa. |
| Menengah, sesuai kebutuhan | [r8-analyzer](https://github.com/android/skills/tree/v1.0.16/performance/r8-analyzer) | Meninjau konfigurasi R8/keep rules untuk build yang dioptimalkan. | Jika build release memakai R8 dan ada masalah ukuran APK, aturan keep, atau crash yang hanya muncul setelah minifikasi. Periksa dahulu apakah task/tool yang diminta skill tersedia pada versi Android Gradle Plugin proyek. |
| Menengah | [android-permissions-security](https://github.com/android/skills/tree/v1.0.16/security/android-permissions-security) | Membantu meninjau izin dan batas akses Android. | Saat menambah atau mengubah akses kalender, notifikasi, musik, wallpaper, penguncian layar, atau fitur lain yang berinteraksi dengan sistem. |
| Menengah, jika UI terkait sistem berubah | [edge-to-edge](https://github.com/android/skills/tree/v1.0.16/system/edge-to-edge) | Referensi untuk inset, status bar, navigation bar, dan tata letak edge-to-edge. | Saat mengubah layout layar penuh atau menemukan konten tertutup system bars. Verifikasi dukungan dan API berdasarkan minSdk/targetSdk proyek. |
| Rendah untuk alur harian saat ini | [android-cli](https://github.com/android/skills/tree/v1.0.16/android-cli) | Panduan untuk alur kerja Android CLI. | Opsional; bukan prasyarat karena build Dislauncher saat ini dilakukan lewat GitHub Actions dan dokumentasi proyek menggunakan Gradle Wrapper. |

> Nama dan isi skill dapat berubah di rilis berikutnya. Buka tautan versi yang dipatok di atas sebelum mengikuti instruksi yang memengaruhi build.

## Alur yang disarankan untuk Dislauncher

1. **Sebelum mengubah kode:** minta AI membaca file yang ada dan skill yang relevan; pertahankan arsitektur, versi, package, dan dependensi yang sudah digunakan.
2. **Untuk perubahan UI Compose:** tentukan perilaku yang harus tetap benar, lalu tambahkan atau jalankan test yang cocok jika infrastruktur pengujian sudah tersedia.
3. **Validasi lewat CI:** jalankan workflow build/lint yang sudah ada. Perubahan dokumentasi saja tidak memerlukan build APK; lihat [BUILDING.md](../BUILDING.md) untuk cakupan workflow.
4. **Untuk regresi performa:** kumpulkan trace pada perangkat, lalu gunakan panduan Perfetto untuk menganalisis bukti. Jangan menebak penyebab performa hanya dari source code.
5. **Untuk perubahan permission:** cocokkan kebutuhan fitur dengan manifest, runtime permission, penanganan penolakan, dan perilaku ketika akses opsional tidak diberikan.
6. **Jangan menambahkan semua skill sekaligus:** gunakan skill yang spesifik pada tugas. Tidak perlu menyalin seluruh repository upstream ke repo aplikasi.

## Menggunakan Android Skills

Android Skills dapat dipasang sebagai instruksi untuk coding agent yang didukung. README upstream menjelaskan opsi Android CLI dan plugin agent:

- [Panduan instalasi resmi](https://github.com/android/skills/tree/v1.0.16#install-android-skills)
- [Dokumentasi Android Skills](https://developer.android.com/tools/agents/android-skills)

Perintah upstream untuk memasang satu skill ke direktori proyek (jalankan hanya jika Android CLI tersedia dan lingkungan agent memang membaca direktori skill tersebut):

```sh
android skills add testing-setup --project=.
```

Jangan menganggap perintah itu sebagai langkah build aplikasi. Instalasi skill hanya menambahkan instruksi untuk agent; itu tidak menambahkan kode atau dependensi ke APK. Karena alur kerja Dislauncher menggunakan GitHub web/mobile dan GitHub Actions, gunakan dokumentasi ini sebagai checklist saat meminta bantuan AI, kecuali ada lingkungan agent yang benar-benar mendukung instalasi skill.

## Batasan dan catatan

- Skill adalah panduan untuk agent, bukan jaminan bahwa semua saran cocok dengan proyek atau versi tool yang digunakan.
- Periksa perubahan terhadap file Gradle, manifest, minSdk/targetSdk, dan workflow aktual sebelum menerapkannya.
- Jangan menaikkan versi AGP, Kotlin, Compose BOM, SDK, atau dependensi hanya karena sebuah skill mencontohkan versi lain.
- Jangan mengaktifkan API eksperimental atau mengubah perilaku launcher sistem tanpa alasan dan pengujian yang jelas.
- Selalu jalankan build/lint yang tersedia setelah perubahan kode dan tinjau hasilnya.

## Referensi

- [Android Skills v1.0.16 — release](https://github.com/android/skills/releases/tag/v1.0.16)
- [Android Skills v1.0.16 — README](https://github.com/android/skills/blob/v1.0.16/README.md)
- [Android Skills — dokumentasi Android Developers](https://developer.android.com/tools/agents/android-skills)
- [Dislauncher — panduan build](../BUILDING.md)
