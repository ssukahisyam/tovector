# VectoForge Studio — Intelligent Raster-to-Vector & Batch Tracing Engine

**VectoForge Studio** adalah aplikasi studio vektorisasi Android yang mengubah gambar raster (PNG, JPG, WebP) menjadi grafik vektor **SVG** presisi tinggi secara tunggal maupun **Batch**. Aplikasi ini mengatasi masalah klasik hasil tracing yang "mbulet" (kusut, kelebihan titik/node, atau sudut tumpul yang tidak semestinya) melalui **5 mesin algoritma tracing spesifik** yang diadaptasi dari teknik **Potrace**, **VTracer**, dan **Inkscape**, dilengkapi inspektur titik kurva Bézier (*Wireframe & Node Inspector*) serta penyuntingan *path/layer* sebelum ekspor.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> Berikut adalah rangkuman keputusan fitur dan alur kerja yang telah dikonfirmasi:

- **Confirmed Decision 1 — 5 Mode Algoritma Tracing Khusus**:
  1. **B/W Clean Logo & Siluet (Gaya Potrace)**: Binarisasi adaptif, *despeckle* (`turdsize`), aproksimasi poligon optimal, deteksi sudut (`alphamax`), dan kurva Bézier kubik halus.
  2. **Geometric & Sharp Line Art (Mode Anti-Mbulet)**: Dirancang khusus untuk gambar simpel/ikon agar garis tidak kusut—menggunakan *pre-smoothing*, penyederhanaan **Douglas-Peucker** agresif, penguncian sudut siku/garis lurus (*orthogonal & collinear snapping*), serta *corner threshold* ketat.
  3. **Multicolor Flat Poster & Ilustrasi (Gaya VTracer)**: Kuantisasi warna hierarkis (*K-Means / Median Cut*), pemisahan layer warna (*Stacked* atau *Cutout*), penghilangan tangga piksel (*staircase removal*), dan perataan tepi antar-layer.
  4. **Pixel Art & Strict Polygon**: Mempertahankan kisi piksel menjadi poligon vektor tajam (tanpa pelengkungan kurva Bézier) dengan penggabungan kontur piksel sewarna.
  5. **Sketch & Centerline Stroke**: Menggunakan *morphological skeletonization* (algoritma penipisan **Zhang-Suen**) untuk mengekstrak garis tengah gambar sehingga menghasilkan `<path>` dengan `stroke` tunggal (bukan *fill* ganda yang mengitari ketebalan garis).
- **Confirmed Decision 2 — Alur Kerja Batch Fleksibel**: Mendukung **Preset Global** untuk seluruh antrean gambar sekaligus, ditambah kemampuan **Custom Override per Gambar** bagi item tertentu yang memiliki karakteristik unik (plus sampel bawaan siap uji untuk berbagai kasus gambar).
- **Confirmed Decision 3 — Inspeksi Vektor Mendalam & Hapus Layer/Path**: Menyediakan **Split-Screen Slider (Before vs After)**, **Wireframe & Node Inspector** (menampilkan titik *anchor* persegi, *handle* kontrol kurva Bézier, dan jumlah node secara *real-time*), **Slider Parameter Manual**, serta kemampuan **menghapus atau menyembunyikan layer warna / path sampah** langsung sebelum disimpan.
- **Confirmed Decision 4 — Ekspor SVG Standar & Paket ZIP Batch**: Setiap gambar dapat diekspor sebagai file `.svg` murni yang bersih dan teroptimasi, atau diekspor sekaligus dalam satu bundel arsip `.zip` untuk seluruh antrean batch.

---

### 1. Overview & Core Concept

- **What It Does**: Mengonversi satu atau puluhan gambar sekaligus menjadi vektor SVG yang bersih, ringan (jumlah node minimal), dan siap diedit di Inkscape, Illustrator, Figma, atau web.
- **Target Audience / Persona**: Desainer grafis, ilustrator, pembuat logo/stiker, pengrajin *cutting/laser/CNC*, dan developer yang sering mendapati hasil *auto-trace* biasa terlalu rumit atau "mbulet" saat menangani gambar sederhana.
- **Key Value**:
  - **Diagnosa & Solusi "Anti-Mbulet"**: Menampilkan metrik jumlah *Path* dan *Total Nodes* secara langsung—pengguna dapat melihat bagaimana mode *Geometric Anti-Mbulet* atau slider *Simplification Tolerance* memangkas ratusan node kusut menjadi belasan titik kurva yang presisi.
  - **Produktivitas Batch + Presisi Mikro**: Memproses banyak aset sekaligus dengan satu klik, namun tetap memberi kendali *per-item* dengan pratinjau *split-screen* dan penghapusan *layer/path* yang tidak diinginkan.

---

### 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Batch Queue & Preset Hub (Layar Utama)**:
     - Pengguna memilih banyak gambar dari galeri (via *Android Photo Picker*) atau memuat **Sample Test Pack** bawaan (Logo Simpel, Ikon Garis, Ilustrasi Warna, Pixel Art, dan Sketsa Tangan) untuk langsung menguji perbedaan tiap algoritma.
     - Memilih **Global Tracing Mode** (5 pilihan mode) dan menyetel parameter global jika diperlukan.
     - Menekan **"Process Batch"** untuk menjalankan pemrosesan paralel menggunakan *Kotlin Coroutines* dengan indikator progres per kartu, lencana status (*Global* vs *Custom Override*), serta statistik ukuran node/path.
  2. **Interactive Vector Studio & Node Inspector (Layar Detail / Override per Gambar)**:
     - Ketuk salah satu gambar di antrean untuk membuka studio inspeksi interaktif.
     - **Canvas Multiview**: Mendukung *Pinch-to-Zoom & Pan*, mode **Split-Screen Slider** (geser kiri/kanan untuk membandingkan Raster Asli vs Hasil Vektor), dan toggle **Wireframe + Bézier Node Overlay** (menampilkan garis kontur, titik *anchor* sudut vs lengkung, dan garis *control handle*).
     - **Parameter Tuning Real-Time**: Panel bawah berisi pemilih 5 Mode Tracing dan slider khusus:
       - *Despeckle (`turdsize`)*: Membuang bintik piksel kecil (0–100 px).
       - *Simplify / Anti-Mbulet Tolerance (`epsilon`)*: Mengatur ketatnya penyederhanaan Douglas-Peucker agar garis lurus tidak bergelombang.
       - *Corner Threshold (`alphamax`)*: Mengatur batas sudut tajam vs lengkungan kurva Bézier.
       - *Luminance Threshold / Color Count*: Ambang gelap-terang (untuk mode B/W & Sketch) atau jumlah palet warna (2–16 warna untuk mode Multicolor).
     - **Layer & Path Pruner**: Daftar *layer* warna dan sub-path yang terdeteksi. Pengguna dapat mengetuk ikon mata/sampah untuk membuang *background* putih atau *path* liar sebelum ekspor.
  3. **Export & Batch ZIP Packaging**:
     - Ekspor gambar tunggal ke `.svg`, bagikan langsung via *Android Share Sheet*, atau salin kode mentah `<svg>...</svg>`.
     - Ekspor seluruh antrean Batch menjadi satu file **`.zip`** berisi kumpulan file `.svg` yang rapi beserta ringkasan laporan konversi.

- **Visual Identity & Theme**:
  - **Aesthetic Direction**: *Precision CAD & Vector Craft Studio* — memadukan estetika studio desain profesional (seperti Figma / Linearity Curve) dengan latar kanvas *blueprint grid* gelap dan aksen warna teknis yang tajam.
  - **Color Palette & Mood**:
    - `--background`: `#0D1117` (Deep Obsidian Canvas) & `#161B22` (Elevated Studio Surface)
    - `--primary`: `#00E5FF` (Cyan Vector Anchor — warna khas garis *wireframe* dan node utama)
    - `--secondary`: `#FF6D00` (Amber Tangents — warna penanda *control handle* Bézier dan lencana *Custom Override*)
    - `--tertiary`: `#10B981` (Emerald Optimized — indikator jumlah node rendah / selesai diproses)
    - Mendukung *Dark Theme* secara penuh sebagai *default* studio visual, serta *Light Theme* beraksen kertas teknik (*Drafting Paper* `#F8FAFC`).
  - **Typography & Hierarchy**:
    - *Display & Headings*: **Space Grotesk** (dibundel lokal via `font-util`) — memberikan karakter geometris dan teknis khas software vektor.
    - *Technical Metrics & SVG Code*: **JetBrains Mono** (dibundel lokal via `font-util`) — untuk angka jumlah node, koordinat kurva, parameter slider, dan pratinjau kode XML/SVG.
    - *Body & Controls*: **Plus Jakarta Sans** — bersih dan mudah dibaca pada label kontrol.
  - **Component Styling & Layout**:
    - Kanvas interaktif menggunakan latar belakang *checkerboard / dot-grid* untuk memperjelas area transparan.
    - *Adaptive Layout*: Pada layar ponsel (*Compact*), menggunakan navigasi tab bawah (*Batch Queue*, *Vector Studio*, *Export & Code*); pada tablet/layar lebar (*Expanded*), menggunakan tampilan *Side-by-Side Split Workbench* (Antrean Batch di kiri, Kanvas Inspektur & Parameter di kanan).

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1 — Mesin Vektorisasi Native Kotlin Murni (5 Pipeline Algoritma Spesifik)**
  - *Chosen Approach*: Membangun mesin pemrosesan matriks piksel dan geometri vektor langsung di Kotlin menggunakan `Dispatchers.Default` yang menggabungkan tahapan terbaik Potrace, VTracer, dan Zhang-Suen.
  - *Why*: Menjamin pemrosesan 100% *offline* di perangkat tanpa batas kuota server, latensi nol saat menggeser slider parameter di *Vector Studio*, serta kendali penuh atas struktur titik kurva (`M`, `L`, `C`, `Z`) untuk ditampilkan di *Node Inspector*.
  - *How "Anti-Mbulet" Works Technically*:
    1. Menghapus *noise* tepi (*anti-aliasing halo*) dengan *thresholding* tegas dan *Connected-Component Labeling (CCL)* untuk membuang pulau piksel `< turdsize`.
    2. Melakukan *Contour Tracing (Moore-Neighbor / Marching Squares)* dengan penghapusan tangga piksel (*staircase removal*).
    3. Menyederhanakan titik poligon menggunakan **Ramer-Douglas-Peucker (RDP)** dan mendeteksi segmen kolinear (garis lurus panjang dikunci menjadi perintah `L` murni, bukan kurva `C` yang melengkung-lengkung).
    4. Menghitung sudut belokan (`alphamax`): sudut tajam dipertahankan sebagai titik patah, sedangkan rentang lengkung dihaluskan dengan *Cubic Bézier Spline Fitting*.

- **Decision 2 — Arsitektur State Batch dengan Global Preset + Per-Item Override**
  - *Chosen Approach*: Setiap item di dalam antrean menyimpan status `useGlobalConfig: Boolean` dan `customConfig: TraceConfig?`.
  - *Why*: Saat pengguna mengubah preset global (misalnya dari *B/W Logo* ke *Geometric Anti-Mbulet*), semua gambar yang berstatus *Global* otomatis mengikuti aturan baru, sedangkan gambar yang sudah disetel khusus di *Vector Studio* tetap aman mempertahankan pengaturannya.

---

### 4. Technical Architecture & Data Strategy

- **Architecture & Component Diagram**:

```
┌───────────────────────────────────────────────────────────────────────────┐
│                         UI LAYER (Jetpack Compose)                        │
├───────────────────────┬───────────────────────────┬───────────────────────┤
│   BatchQueueScreen    │    VectorStudioScreen     │  SvgInspectorDialog   │
│  • Multi-Photo Picker │  • Zoom/Pan Vector Canvas │  • Raw SVG Code View  │
│  • Sample Asset Pack  │  • Before/After Slider    │  • One-Tap Copy SVG   │
│  • Global Mode Bar    │  • Wireframe & Node View  │  • Share / Save File  │
│  • Batch Progress     │  • Layer/Path Pruner      │  • Batch ZIP Exporter │
└───────────┬───────────┴─────────────┬─────────────┴───────────┬───────────┘
            │                         │                         │
            ▼                         ▼                         ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                     STATE & VIEWMODEL (VectoForgeViewModel)               │
│  • StateFlow<WorkspaceUiState> (Batch Items, Global Config, Active Item)  │
│  • Parallel Batch Processor (Coroutines Semaphore + Dispatchers.Default)  │
│  • Preset Manager (5 Built-in Modes + Custom Parameter Sliders)           │
└─────────────────────────────────────┬─────────────────────────────────────┘
                                      │
                                      ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                    CORE TRACING ENGINE (VectorTracingEngine)              │
├──────────────────┬──────────────────┬──────────────────┬──────────────────┤
│ 1. Preprocessor  │ 2. Contour &     │ 3. Geometry &    │ 4. SVG & ZIP     │
│ • Luminance /    │    Skeletonizer  │    Spline Fitter │    Serializer    │
│   Otsu Threshold │ • Marching Sq.   │ • Staircase Drop │ • Clean <svg>    │
│ • K-Means Color  │   Boundary Walk  │ • Douglas-Peucker│   Path Builder   │
│   Quantization   │ • Zhang-Suen     │ • Corner Detect  │ • Coordinate     │
│ • CCL Despeckle  │   Thinning       │ • Cubic Bézier & │   Rounding       │
│   (Turdsize)     │   (Centerline)   │   Line Snapping  │ • ZipOutputStream│
└──────────────────┴──────────────────┴──────────────────┴──────────────────┘
```

- **Data Model & State**:
  - `TraceMode`: Enum 5 mode (`POTRACE_BW`, `GEOMETRIC_SHARP`, `VTRACER_MULTICOLOR`, `PIXEL_POLYGON`, `CENTERLINE_SKETCH`).
  - `TraceConfig`: Menyimpan `mode`, `threshold` (0..255), `colorCount` (2..16), `despeckleSize` (0..100), `simplifyTolerance` (0.5f..8.0f), `cornerThreshold` (30f..160f), `smoothness` (0f..1.5f), dan `invert` (Boolean).
  - `VectorPathElement`: Merepresentasikan satu `<path>` atau layer warna hasil tracing, berisi daftar segmen (`MoveTo`, `LineTo`, `CubicTo`, `Close`), warna *fill/stroke*, jumlah node, luas area, dan flag `isVisible` (untuk fitur hapus/sembunyikan layer).
  - `BatchImageItem`: Menyimpan `id`, `title`, `sourceBitmap`, `useGlobalConfig`, `overrideConfig`, `status` (`IDLE`, `PROCESSING`, `COMPLETED`, `ERROR`), serta `traceResult` (`VectorTraceResult` berisi daftar `VectorPathElement`, dimensi kanvas, total node, waktu proses ms, dan string SVG).

- **Interactive Component & State Mapping**:
  - **Pemilihan Gambar & Sampel**: Menambahkan gambar dari galeri (`PickMultipleVisualMedia`) atau memilih sampel bawaan akan memasukkan `BatchImageItem` ke antrean dan langsung memicu pratinjau tracing.
  - **Perubahan Mode / Slider di Vector Studio**: Menggunakan *debounced coroutine job* (150ms) agar setiap geseran slider (*Despeckle*, *Simplify*, *Corner*, *Threshold*) langsung memperbarui kanvas vektor, overlay titik Bézier, dan penghitung jumlah node tanpa *lag* UI.
  - **Penghapusan Layer / Path**: Mengetuk tombol hapus/mata pada daftar layer di *Vector Studio* akan memfilter `VectorPathElement` tersebut dari render kanvas dan meregenerasi output string `.svg` secara instan.
  - **Ekspor Tunggal & ZIP Batch**: Menggunakan `ActivityResultContracts.CreateDocument` (`image/svg+xml` untuk tunggal dan `application/zip` untuk batch) serta `FileProvider` + `Intent.ACTION_SEND` untuk berbagi langsung ke aplikasi lain.
