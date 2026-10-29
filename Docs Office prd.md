Docs Office

Documentation suite

Documents

Platform: Android (Kotlin)\
UI: Jetpack Compose\
Arch: MVVM + Room, offline-first

PRDProduct Requirements Document

# Docs Office: Product Requirements

What the app does, who it is for, what is in scope for each release, and how success is measured.

Version 1.0Author: Dev (Foodma Technologies)Platform: Android, Kotlin + Jetpack ComposeOctober 2026

1\. Overview

**Docs Office** is a fast, offline-first Android app that finds every document on a phone, sorts them, and opens them without a heavyweight office suite. It is built natively in Kotlin with Jetpack Compose and treats the phone as the main place people read documents.

**Mission:** any document on the phone should be findable in two taps and readable in about two seconds, on a 2 GB device with no data connection.

The app is a viewer and organiser first. Editing Office files is out of scope for the first release; the product wins by being quick, small and reliable at the things people do most: find, open, read, share.

2\. Problem

| Problem | What happens today | What Docs Office does |
| --- | --- | --- |
| **Documents are scattered** | Files arrive by WhatsApp, Telegram, email, browser downloads and Bluetooth, each landing in a different folder. | One library, with smart collections by source (Downloads, WhatsApp, Telegram) and by type. |
| **Files are hard to recognise** | Names such as `Document (3).pdf` or `IMG-2026…` say nothing about the content. | Recents, favorites, name search, and from 1.1 search inside the document text. |
| **Office suites are heavy** | Full suites take a large share of storage on 32 to 64 GB phones and are slow on low-end hardware. | Lightweight native viewers. Target download size of 25 MB or less. |
| **Data is expensive** | Cloud-first apps need an account and a connection just to open a local file. | Offline-first. No account required. Nothing leaves the device. |

3\. Target users

PDFStudents

Lecture notes, past questions and textbooks that arrive over WhatsApp and need to be found again before exams.

DOCOffice staff and civil servants

Memos, circulars and spreadsheets received on the phone and opened in a hurry between meetings.

XLSSmall business owners

Invoices, receipts, price lists and stock sheets shared with customers and suppliers.

PPTFreelancers and professionals

Contracts, CVs, proposals and decks that must open correctly on a client's request, anywhere.

4\. Goals and non-goals

### Goals

- **G1.** Find any document in two taps or fewer.
- **G2.** Open PDF, Word, Excel, PowerPoint, text and images offline.
- **G3.** Stay small and quick on low-end phones.
- **G4.** Keep documents private: nothing is uploaded.

### Non-goals for 1.0

- Editing Word, Excel or PowerPoint files.
- Cloud storage, accounts or collaboration.
- Legally binding e-signature workflows.
- iOS, web and desktop clients.

5\. Feature scope

Release labels: MVP first public build, 1.1 Office viewers and content search, 1.2 scanning, tools and vault, 2.0 later.

| Feature | Description | Release |
| --- | --- | --- |
| **Auto-discovery** | Finds supported documents across the phone in the background, with incremental updates. | MVP |
| **Smart collections** | Categories (PDF, Word, Excel, Slides, Text, Images) and sources (Downloads, WhatsApp, Telegram, Scans). | MVP |
| **Recents and favorites** | Reopen what was read last; pin what matters. | MVP |
| **PDF, text and image viewers** | Smooth zoom, page jump, last-page memory, reader themes, password-protected PDFs. | MVP |
| **File actions** | Rename, copy, move, share, delete to a 30-day in-app trash. | MVP |
| **Open from other apps** | Registers as a handler so files opened from WhatsApp or the browser launch in Docs Office. | MVP |
| **Name search** | Instant results as the user types, with type and source filters. | MVP |
| **Word, Excel, PowerPoint viewers** | Read-only rendering of .docx, .xlsx and .pptx without an external suite. | 1.1 |
| **Content search** | Full-text search across indexed documents with highlighted snippets. | 1.1 |
| **Tags, collections, bookmarks** | User-defined organisation and reading progress. | 1.1 |
| **Document scanner and OCR** | Capture pages, crop, enhance, save as searchable PDF. | 1.2 |
| **PDF tools** | Merge, split, reorder, rotate, compress, images to PDF. | 1.2 |
| **Private vault** | Encrypted storage unlocked by fingerprint or PIN. | 1.2 |
| **Hausa, Yoruba, Igbo** | Interface translations alongside English. | 1.2 |
| **Backup and sync, annotation, eBooks** | Optional cloud backup, PDF markup, EPUB reading. | 2.0 |

6\. Key user stories

- As a student, I want my WhatsApp PDFs in one list so I can find last week's lecture note without scrolling a chat.
- As a shop owner, I want to open an invoice spreadsheet from a customer and see the numbers without installing a large app.
- As an office worker, I want to search for the word "circular" and see every document that contains it.
- As a professional, I want to scan a signed page and save it as a PDF I can email at once.
- As a user with a shared phone, I want a vault so private documents stay out of the main library.

7\. Success metrics

Targets below are proposed starting points to be confirmed after the closed beta.

| Metric | Target | How it is measured |
| --- | --- | --- |
| **Cold start to Home** | 1.5 s or less on a 2 GB device | Macrobenchmark on the reference low-end device |
| **Open a recent PDF** | 1 s or less (median) | Macrobenchmark and in-app timing |
| **Crash-free sessions** | 99.5% or higher | Play Console vitals, Crashlytics |
| **Day-30 retention** | 20% or higher | Play Console, opt-in analytics |
| **Play Store rating** | 4.4 or higher | Play Console |
| **Download size** | 25 MB or less | App bundle size report in CI |

8\. Business model

Freemium. Browsing, viewing, favorites, name search and file actions stay free. A Pro tier covers OCR scanning without limits, PDF tools, the vault and extra reader themes. If ads are used at all, they appear only in browse lists and never inside a document. Billing runs through Google Play Billing with prices set in naira; shorter billing periods are worth testing given how people in this market tend to pay.

9\. Product risks

| Risk | Why it matters | Response |
| --- | --- | --- |
| **Play policy on all-files access** | Library-wide discovery needs broad storage access, which Play reviews closely. | Declare the document management use case with a demo video; ship a folder-picker fallback. |
| **Office format fidelity** | Custom viewers will not match Microsoft Office pixel for pixel. | Position as a reader; always offer "Open with…" for complex files; build a test corpus early. |
| **Low-end memory limits** | Large PDFs and sheets can run a 2 GB phone out of memory. | Page-level rendering, downsampled bitmaps, hard memory budgets per viewer. |
| **Name similarity** | "Docs Office" echoes Microsoft Office and Google Docs, which can trigger impersonation or trademark complaints. | Run a trademark search and review Play's impersonation policy before the store listing; keep an alternative name ready. |
| **Expectation of editing** | Users may assume a document app edits documents. | State "viewer and organiser" in the listing; plan annotation for 2.0. |

SRSSoftware Requirements Specification

# Docs Office: Software Requirements

Testable functional and non-functional requirements, permissions, supported formats and error behaviour.

Version 1.0Traces to: PRD v1.0Requirement IDs: FR-\<module>-nn

1\. Scope and context

Docs Office runs entirely on the device. Its external interfaces are the Android storage APIs (file system, MediaStore, Storage Access Framework), other apps that send it files through intents, the Google Play services used for billing and scanning, and the user.

| Actor | Interaction |
| --- | --- |
| **User** | Browses, searches, reads, organises and shares documents. |
| **Other apps** | Send a file to Docs Office (`ACTION_VIEW`, `ACTION_SEND`) or receive a file shared from it. |
| **Android system** | Provides storage, background scheduling, biometrics, notifications and the share sheet. |
| **Google Play** | Distributes the app, handles purchases, and supplies the on-device scanner and text recognition modules. |

2\. Functional requirements

Priority labels follow the PRD: MVP 1.1 1.2.

### Discovery and indexing

| ID | Requirement | Release |
| --- | --- | --- |
| FR-DI-01 | On first launch the app explains why storage access is needed, requests it, then scans for supported files. | MVP |
| FR-DI-02 | The scan runs in the background, can be resumed after the process is killed, and reports progress without blocking the UI. | MVP |
| FR-DI-03 | New, changed and deleted files are detected incrementally using a content observer plus a periodic reconcile. | MVP |
| FR-DI-04 | Each file is classified by extension and verified by header bytes; mismatches are flagged rather than hidden. | MVP |
| FR-DI-05 | Each file is tagged with a source (Downloads, WhatsApp, Telegram, Scans, Other) derived from its path. | MVP |
| FR-DI-06 | The user can exclude folders; common noise (caches, thumbnails, hidden directories) is excluded by default. | MVP |
| FR-DI-07 | Text is extracted from supported documents in the background, only while the device is idle and either charging or above 30% battery. | 1.1 |

### Browse and organise

| ID | Requirement | Release |
| --- | --- | --- |
| FR-BO-01 | Home shows Recents, Favorites and category tiles with live counts. | MVP |
| FR-BO-02 | The Files tab offers category and folder views, list and grid layouts, and sorting by name, date and size. | MVP |
| FR-BO-03 | The user can rename, copy, move and share a file. Delete moves it to an in-app trash. | MVP |
| FR-BO-04 | Trashed files can be restored for 30 days, then are purged automatically. | MVP |
| FR-BO-05 | Favoriting is a single tap from any list and from the viewer. | MVP |
| FR-BO-06 | Long-press starts multi-select with bulk share, move and delete. | MVP |
| FR-BO-07 | The user can create tags and collections and assign several to one document. | 1.1 |

### Viewer

| ID | Requirement | Release |
| --- | --- | --- |
| FR-VW-01 | PDFs open with pinch zoom, double-tap zoom, page jump, thumbnails, and restore the last page read. | MVP |
| FR-VW-02 | Password-protected PDFs prompt for the password. The password is never stored. | MVP |
| FR-VW-03 | TXT, MD, CSV, JSON and LOG files open with adjustable font size and monospace option. | MVP |
| FR-VW-04 | JPG, PNG, WebP and GIF open with pinch zoom and swipe to the next image in the folder. | MVP |
| FR-VW-05 | Light, dark and sepia reader themes. | MVP |
| FR-VW-06 | Files the app cannot render show their details and an "Open with…" action. | MVP |
| FR-VW-07 | The app is a selectable handler for supported file types opened from other apps. | MVP |
| FR-VW-08 | DOCX viewer renders headings, paragraphs, lists, tables and embedded images. | 1.1 |
| FR-VW-09 | XLSX viewer shows sheet tabs, a frozen header row, resizable columns and cell values as displayed. It does not edit. | 1.1 |
| FR-VW-10 | PPTX viewer shows slides in order with text and images, and speaker notes when present. | 1.1 |
| FR-VW-11 | In-document find, bookmarks and reading progress. | 1.1 |

### Search

| ID | Requirement | Release |
| --- | --- | --- |
| FR-SR-01 | Name search returns results as the user types, within 200 ms for a library of 50,000 documents. | MVP |
| FR-SR-02 | Results can be filtered by type, source and date range. | MVP |
| FR-SR-03 | Recent searches are kept on the device and can be cleared. | MVP |
| FR-SR-04 | Content search returns matches with the matching text highlighted in a snippet. | 1.1 |

### Scan, tools and vault

| ID | Requirement | Release |
| --- | --- | --- |
| FR-SC-01 | Capture one or more pages, crop and enhance, and save as a PDF in the Scans collection. | 1.2 |
| FR-SC-02 | Run text recognition on scans so the PDF becomes searchable. | 1.2 |
| FR-SC-03 | Merge, split, reorder and rotate PDF pages into a new file; the original is never overwritten. | 1.2 |
| FR-SC-04 | Compress a PDF using Low, Medium and High quality presets and show the size saved. | 1.2 |
| FR-SC-05 | Convert one or more images into a single PDF. | 1.2 |
| FR-VT-01 | Documents moved to the vault are encrypted, removed from the library and invisible to other apps. | 1.2 |
| FR-VT-02 | The vault unlocks with biometrics or a PIN and locks again when the app goes to the background. | 1.2 |

### Settings

| ID | Requirement | Release |
| --- | --- | --- |
| FR-ST-01 | Theme: follow system, light, or dark. | MVP |
| FR-ST-02 | Excluded folders, default sort, and trash retention. | MVP |
| FR-ST-03 | Analytics and crash reporting are opt-in or opt-out in one place, and never include file names. | MVP |
| FR-ST-04 | Language: English at launch, Hausa, Yoruba and Igbo in 1.2. | 1.2 |

3\. Non-functional requirements

| Category | Requirement | Target |
| --- | --- | --- |
| **Performance** | Cold start to a usable Home screen | 1.5 s or less on a 2 GB device |
| **Performance** | Initial scan of 10,000 files while the UI stays responsive | 60 s or less; lists scroll at 60 fps throughout |
| **Memory** | Peak memory used by a viewer | 96 MB or less; bitmaps downsampled; no out-of-memory crash on a 2 GB device |
| **Battery** | Background indexing | Deferred when battery is low; never holds a wake lock for longer than a work slice |
| **Size** | Download size | 25 MB or less |
| **Offline** | All core features without a network | 100% of FR-DI, FR-BO, FR-VW, FR-SR |
| **Reliability** | File operations never lose a document | Delete goes to trash first; moves are copy-verify-delete; crash-free sessions 99.5% or higher |
| **Privacy** | No document content or file name leaves the device | Verified by network inspection in the test plan |
| **Security** | Vault encryption | AES-256-GCM, keys held in the Android Keystore; no plaintext in logs or caches |
| **Compatibility** | Android versions | minSdk 24 (Android 7.0); targetSdk set to the level Play currently requires |
| **Accessibility** | Screen reader, touch and contrast | TalkBack labels on every control, 48 dp touch targets, 4.5:1 text contrast, usable at 200% font scale |
| **Localisation** | Strings and layout | All text externalised; layouts tolerate longer translations and right-to-left scripts |

4\. Permissions and Android constraints

| Permission | Purpose | If unavailable |
| --- | --- | --- |
| MANAGE_EXTERNAL_STORAGE | Discover documents across Downloads, WhatsApp and Telegram folders. Documents are not covered by the media permissions on Android 13 and above. | Fall back to a folder picker (`ACTION_OPEN_DOCUMENT_TREE`) with persisted access; discovery is limited to chosen folders. |
| READ_MEDIA_IMAGES | Image documents on Android 13 and above. | System photo picker for individual images. |
| READ / WRITE_EXTERNAL_STORAGE | Legacy storage access (read up to Android 12, write up to Android 9). | Not applicable on newer versions. |
| USE_BIOMETRIC | Unlock the vault. | PIN only. |
| POST_NOTIFICATIONS | Optional progress notice for long scans or compression, requested only on first need. | Work continues silently. |
| INTERNET | Play Billing and crash reporting only. No document data is sent. | App works fully offline. |

**Policy check:** Google Play requires a declaration and review for all-files access. Document management apps are among the permitted uses, but the policy text changes, so confirm the current wording and prepare the demo video before the first review. The scanner module runs in Play services and does not need the camera permission.

5\. Supported formats

| Format | Extensions | Approach | Release |
| --- | --- | --- | --- |
| PDF | .pdf | Platform PDF renderer, with the Jetpack PDF viewer where stable | MVP |
| TXT | .txt .md .csv .json .log .xml | Virtualised text rendering in Compose | MVP |
| IMG | .jpg .jpeg .png .webp .gif | Coil with a zoomable surface | MVP |
| DOC | .docx | Custom OOXML reader producing Compose blocks | 1.1 |
| XLS | .xlsx | Custom OOXML sheet reader with a virtualised grid | 1.1 |
| PPT | .pptx | Custom OOXML slide reader | 1.1 |
| ZIP | .zip | Browse and extract | 1.2 |
| OLD | .doc .xls .ppt | Listed in the library; opened through "Open with…" | MVP |
| EPUB | .epub | Reflow reader | 2.0 |

6\. Error behaviour

| Condition | Required behaviour |
| --- | --- |
| **Corrupt or truncated file** | Show "This file can't be opened" with file details, Open with… and Share. Log the error type only. |
| **Password-protected file** | Prompt for the password; allow retry; do not store it. |
| **Very large file** | PDFs stream page by page. Text files above a size limit open the first part with a notice and an option to continue. |
| **File deleted outside the app** | Remove from the library at the next reconcile; if open, show a clear message and close the viewer. |
| **Storage access revoked** | Show the cached library read-only with a prominent action to restore access. |
| **Low free space** | Block copy, scan save and compression with a message that states how much space is needed. |
| **Renderer out of memory** | Release caches, retry once at lower resolution, then show the corrupt-file screen. |

7\. Sample acceptance criteria

- **FR-DI-03.** Given the library is loaded, when a PDF is downloaded to the Downloads folder, then it appears under Recents and Downloads within 10 seconds without a manual refresh.
- **FR-BO-04.** Given a document was deleted 29 days ago, when the user opens Trash, then Restore returns it to its original folder; at 31 days it is gone.
- **FR-VW-02.** Given an encrypted PDF, when the user enters a wrong password, then an inline error appears and the field stays focused; a correct password opens page 1.
- **FR-SR-01.** Given 50,000 indexed documents, when the user types three characters, then the first results render in 200 ms or less.

TDDTechnical Design Document

# Docs Office: Technical Design

Stack, architecture, storage strategy, data model, rendering pipeline, and the decisions behind them.

Version 1.0Language: KotlinUI: Jetpack Compose + Material 3Pattern: MVVM, unidirectional data flow

1\. Technology stack

Library versions are pinned in a Gradle version catalog at kickoff, using the latest stable release of each at that time.

| Area | Choice | Notes |
| --- | --- | --- |
| **Language** | Kotlin 2.x, Coroutines, Flow | K2 compiler, structured concurrency everywhere |
| **UI** | Jetpack Compose, Material 3 | Compose BOM; type-safe Navigation Compose |
| **Architecture** | MVVM with unidirectional data flow, repositories | Multi-module, feature-per-module |
| **Dependency injection** | Hilt | Constructor injection; test doubles via Hilt testing |
| **Database** | Room with FTS4, Paging 3 | FTS4 because Room supports it directly; see decisions |
| **Preferences** | DataStore | Theme, sort, excluded folders, reader settings |
| **Background work** | WorkManager | Scan, thumbnails, text extraction, trash purge |
| **Images** | Coil | Thumbnails with a bounded disk cache |
| **PDF** | Platform PdfRenderer; Jetpack PDF viewer where stable | Text extraction and page edits through an Apache-licensed library |
| **Scan and OCR** | ML Kit Document Scanner and Text Recognition | Scanner runs in Play services; choose bundled or unbundled recognition models by size budget |
| **Security** | Android Keystore, Tink, BiometricPrompt | Vault encryption |
| **Billing** | Google Play Billing Library | Single Pro entitlement |
| **Observability** | Crashlytics, opt-in analytics, Timber | No file names or paths in events |
| **Build** | Gradle Kotlin DSL, convention plugins, R8, Baseline Profiles | Size and startup are product requirements |

2\. Architecture

**UI layer**Compose screens, ViewModels exposing `StateFlow<UiState>`, one-shot effects through a channel.

events down, state up

**Domain layer**Plain Kotlin models and use cases such as `OpenDocument`, `MoveToTrash`, `SearchDocuments`. No Android imports.

interfaces

**Data layer**Repositories over Room, DataStore, the file layer (file system, MediaStore, SAF) and the renderer plugins.

Module layout

docs-office/ ├─ app/ # Application, navigation graph, Hilt wiring ├─ build-logic/ # Gradle convention plugins ├─ core/ │ ├─ model/ # Pure Kotlin data classes │ ├─ common/ # Dispatchers, Result types, extensions │ ├─ database/ # Room entities, DAOs, FTS, migrations │ ├─ datastore/ # User preferences │ ├─ files/ # Storage access, file operations, trash │ ├─ crypto/ # Vault encryption │ └─ designsystem/ # Theme, components, icons ├─ renderer/ │ ├─ api/ # DocumentRenderer, RenderSession │ ├─ pdf/ text/ image/ │ └─ ooxml/ # docx, xlsx, pptx readers └─ feature/ ├─ onboarding/ home/ files/ search/ └─ viewer/ scan/ vault/ settings/

Feature modules depend on `core` and `renderer:api` but never on each other; the app module owns navigation between them. The renderer plugins are the only code that knows about a specific file format.

3\. Storage access strategy

The file layer hides how a document was found behind one interface, so the rest of the app only sees a `DocumentSource`.

- **Primary:** with all-files access granted, walk well-known roots (Downloads, Documents, the WhatsApp and Telegram document folders) using the extension allowlist and the exclusion list.
- **Fallback:** without it, query `MediaStore.Files` for what it exposes and let the user add folders through `ACTION_OPEN_DOCUMENT_TREE`, persisting the granted URIs.
- **Identity:** a document's id is a stable hash of its location plus size and modified time, so renames and moves are detected as updates, not as delete plus add.
- **Writes:** every mutation goes through one `FileOperations` class that copies, verifies size, then deletes, and records the result in the trash table.

4\. Discovery and indexing pipeline

1. Enumerate roots from the active `DocumentSource` implementations.
2. Filter by extension allowlist and exclusions before touching file metadata.
3. Diff against Room by location, size and modified time to produce added, changed and removed sets.
4. Upsert in batches of about 500 inside one transaction so the library appears progressively.
5. Enqueue thumbnail and page-count jobs for new documents, throttled to two at a time.
6. When the device is idle and charging (or above 30% battery), enqueue text extraction and write results to the FTS table.
7. A content observer triggers a delta scan on change; a nightly reconcile catches anything missed.

5\. Data model (Room)

documents

id

TEXT PK

stable hash

uri

TEXT

path or content URI

displayName

TEXT

indexed for name search

extension, mimeType

TEXT

mimeType indexed

sizeBytes

INTEGER

modifiedAt, addedAt

INTEGER

epoch millis

lastOpenedAt

INTEGER?

drives Recents; indexed

isFavorite

INTEGER

boolean

source

TEXT

DOWNLOADS, WHATSAPP, TELEGRAM, SCANS, OTHER

pageCount

INTEGER?

trashedAt

INTEGER?

null when not in trash

document_text + document_text_fts

rowid

INTEGER

docId

TEXT

FK documents

body

TEXT

FTS4 content

tags, document_tags

tagId

INTEGER

name, color

TEXT

docId, tagId

COMPOSITE

cross reference

Smaller tables: `bookmarks` (docId, page, label), `reading_progress` (docId, position, updatedAt), `search_history` (query, at), `vault_items` (id, encryptedName, blobPath, createdAt) and `excluded_folders` (path). Schema export is on, and every migration has a test.

6\. Rendering pipeline

Every format is a plugin behind one interface. A renderer returns a session whose shape matches how the format is read: paged (PDF, slides), reflowing (Word, text) or grid (spreadsheets). The viewer screen picks the matching Compose surface.

renderer/api

interface DocumentRenderer { val supportedMimeTypes: Set\<String> suspend fun open(source: DocumentSource, password: String? = null): RenderSession } sealed interface RenderSession : Closeable { class Paged( val pageCount: Int, val renderPage: suspend (index: Int, widthPx: Int) -> Bitmap, ) : RenderSession class Reflow(val blocks: Flow\<DocBlock>) : RenderSession class Grid(val sheets: List\<SheetRef>, val readRows: suspend (SheetRef, IntRange) -> List\<Row>) : RenderSession } // Typed failures the viewer maps to the error screens in the SRS sealed class OpenFailure : Exception() { object PasswordRequired : OpenFailure() object Corrupt : OpenFailure() object Unsupported : OpenFailure() object TooLarge : OpenFailure() }

| Format | Session | Technique | Main risk |
| --- | --- | --- | --- |
| **PDF** | Paged | Render one page bitmap at screen width; keep three pages in an LRU cache | Memory on very large pages |
| **DOCX** | Reflow | Unzip, stream `document.xml` with a pull parser, emit blocks; resolve styles, numbering and images lazily | Complex layouts, text boxes, tracked changes |
| **XLSX** | Grid | Read the shared strings table once, stream rows per sheet on demand, use cached cell values | Huge sheets, custom number formats |
| **PPTX** | Paged | Parse each slide's shapes, draw text and pictures onto a canvas at the slide aspect ratio | Fonts, grouped shapes, charts, SmartArt |
| **Text** | Reflow | Memory-map the file, split lines lazily in a `LazyColumn` | Giant single-line files |

7\. Code sketches

core/database: entity and search query

@Entity( tableName = "documents", indices = \[Index("mimeType"), Index("lastOpenedAt"), Index("displayName")\], ) data class DocumentEntity( @PrimaryKey val id: String, val uri: String, val displayName: String, val extension: String, val mimeType: String, val sizeBytes: Long, val modifiedAt: Long, val lastOpenedAt: Long? = null, val isFavorite: Boolean = false, val source: DocSource, val trashedAt: Long? = null, ) @Dao interface DocumentDao { @Query("SELECT * FROM documents WHERE trashedAt IS NULL AND lastOpenedAt IS NOT NULL ORDER BY lastOpenedAt DESC LIMIT :limit") fun recents(limit: Int): Flow\<List\<DocumentEntity>> @Query(""" SELECT d.\* FROM documents d JOIN document_text t ON t.docId = d.id JOIN document_text_fts f ON f.rowid = t.rowid WHERE document_text_fts MATCH :query AND d.trashedAt IS NULL """) fun searchContent(query: String): PagingSource\<Int, DocumentEntity> }

feature/home: ViewModel and screen

@HiltViewModel class HomeViewModel @Inject constructor( private val documents: DocumentRepository, ) : ViewModel() { val state: StateFlow\<HomeState> = combine( documents.recents(limit = 10), documents.favorites(), documents.categoryCounts(), ) { recents, favorites, counts -> HomeState.Ready(recents, favorites, counts) } .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState.Loading) } @Composable fun HomeScreen(vm: HomeViewModel = hiltViewModel(), onOpen: (String) -> Unit) { val state by vm.state.collectAsStateWithLifecycle() when (val s = state) { HomeState.Loading -> HomeSkeleton() is HomeState.Ready -> HomeContent(s, onOpen) } }

8\. Performance plan

- **Startup:** Home renders from Room before any scan work starts; ship a Baseline Profile and measure with Macrobenchmark on a 2 GB reference phone.
- **Lists:** Paging 3 plus stable keys in `LazyColumn`; thumbnails decoded at display size and cached on disk with a size cap.
- **Memory:** each renderer gets an explicit budget; bitmaps are recycled through a pool; viewers release everything in `onCleared`.
- **Battery:** background work uses WorkManager constraints; text extraction is chunked into short slices that checkpoint progress.
- **Size:** R8 full mode, resource shrinking, per-ABI splits through the app bundle, a CI check that fails the build above the size budget, and no bundled fonts beyond a subset for headings.

9\. Security and privacy

- **Vault:** files are encrypted with streaming AEAD (Tink) using a key wrapped by the Android Keystore and gated by biometric or PIN. Jetpack Security's `EncryptedFile` is deprecated, so it is not used.
- **No uploads:** document bytes and names never reach the network. A test asserts that the only outbound hosts are Play services and the crash reporter.
- **Hardening:** parsers run with size and depth limits to resist zip bombs and malformed XML; external entities are disabled; viewers never execute macros or embedded content.
- **Logging:** Timber in release builds drops everything below warning, and no log line contains a path or file name.

10\. Key decisions

| Decision | Choice | Reason and rejected option |
| --- | --- | --- |
| **Office parsing** | Small read-only OOXML parsers | Apache POI adds tens of megabytes and has Android compatibility friction; a reader needs only a subset of the format. |
| **PDF engine** | Platform renderer first | Zero size cost. MuPDF is excellent but AGPL; PDFium wrappers add size. Revisit only if fidelity complaints appear. |
| **Full-text search** | Room FTS4 | Supported natively by Room. FTS5 needs a bundled SQLite build and extra size for modest gain at this scale. |
| **Modules** | Multi-module from the first commit | Enforces the renderer boundary and keeps builds fast; a flat module would be quicker to start but harder to split later. |
| **Storage access** | All-files access with folder-picker fallback | Folder-picker only would make "find every document" impossible on current Android. |
| **Accounts** | None in 1.x | Keeps the privacy promise and removes backend cost and risk. |

UI/UXDesign Specification

# Docs Office: UI/UX Specifications

Design principles, navigation, key screens, tokens, components and states.

Version 1.0Material 3 on Jetpack ComposeDesign target: 360 dp wide phones first

1\. Design principles

Reach in two taps

Home puts Recents and Favorites first. Every document is one search or one category away.

Type is the map

File-type colour and label sit on every row so users recognise a PDF or a sheet before reading the name.

Light on every phone

Few images, no heavy animation, lists that scroll smoothly on low-end hardware.

Calm reading

The viewer hides chrome while reading and returns it with one tap.

2\. Navigation

A bottom bar with four destinations: **Home**, **Files**, **Search** and **More** (Vault, Trash, Settings). Scanning is a floating action on Home and Files. The viewer is a full-screen destination that hides the bottom bar. Back from the viewer returns to the list at the same scroll position.

Onboarding ─▶ Home ─┬─▶ Viewer ├─▶ Files ─▶ Category / Folder ─▶ Viewer ├─▶ Search ─▶ Viewer ├─▶ Scan ─▶ Review ─▶ Save as PDF ─▶ Viewer └─▶ More ─▶ Vault · Trash · Settings

3\. Key screens

Docs Office**Home**

Search documents

Recents

PDF

Invoice_0412.pdf

2.1 MB · WhatsApp

DOC

Lecture 5 notes.docx

480 KB · Downloads

XLS

Stock list Oct.xlsx

96 KB · Telegram

Categories

PDF128

DOC42

XLS17

Scan

HomeFilesSearchMore

Docs Office**Files**

AllDownloadsWhatsAppTelegram

PDF**PDF**128

DOC**Word**42

XLS**Excel**17

PPT**Slides**9

TXT**Text**31

IMG**Images**210

Sort: Date modified

PDF

Past questions 2024.pdf

Yesterday · 5.4 MB

PPT

Q3 review.pptx

Mon · 3.2 MB

HomeFilesSearchMore

Invoice_0412.pdf**Page 3 of 12**

ThumbnailsFindThemeShare

Docs Office**Search**

invoice

AllPDFExcelThis month

PDF

Invoice_0412.pdf

…total due ₦45,000 by 30 Oct…

XLS

Customer invoices.xlsx

Sheet 2 · 14 matches

DOC

Invoice template.docx

Name match

HomeFilesSearchMore

| Screen | Purpose | Key elements |
| --- | --- | --- |
| **Onboarding** | Explain and request storage access | Three short panels, one clear "Allow access" action, a "Choose folders instead" link |
| **Home** | Resume and jump | Search field, Recents row, Favorites, category tiles with counts, Scan action |
| **Files** | Browse by type, source or folder | Source chips, category grid, list and grid toggle, sort menu, multi-select bar |
| **Search** | Find by name or content | Field with recent searches, type and date filters, snippets with highlights |
| **Viewer** | Read | Tap to show or hide bars, page indicator, thumbnails, find, theme, share, favorite |
| **Scan review** | Check pages before saving | Page strip, crop, filter, reorder, save as PDF |
| **Vault** | Private documents | Unlock sheet, list of vault items, move out action |
| **Trash** | Recover deletions | Days left per item, restore, empty trash |

4\. Visual language

A deep teal-blue brand colour carries navigation and primary actions. An amber accent is reserved for the scan action and highlights in search. File-type colours follow the convention people already know from Office and PDF icons, so they act as information, not decoration.

Brand#0E7490

Accent#D97706

PDF#DC2626

Word#2563EB

Excel#16A34A

Slides#EA580C

Images#7C3AED

Text#64748B

| Token | Spec |
| --- | --- |
| **Typography** | System sans for body and lists to keep the app small; a subset of one display face for screen titles only. Material 3 scale: Title 22, Section 16, Body 14, Caption 12. Sizes use `sp` so system font scaling applies. |
| **Spacing** | 4 dp grid. Screen margin 16 dp. List row height 64 dp; touch targets at least 48 dp. |
| **Shape** | 8 dp for rows and tiles, 16 dp for sheets, full radius for chips and the scan action. |
| **Elevation** | Flat lists; elevation only on the scan action, bottom sheets and dialogs. |
| **Themes** | Light, dark, and a sepia reader theme. Dynamic colour is optional and never changes file-type colours. |
| **Motion** | 150 to 250 ms standard transitions. Honour the system "remove animations" setting. |

5\. Components

| Component | Behaviour |
| --- | --- |
| **DocumentRow** | File-type chip, name (two lines max, middle-ellipsis on the extension), size, date and source. Trailing favorite star. Long-press enters selection. |
| **CategoryTile** | File-type chip, label and live count. Opens the category list. |
| **SourceChip** | Filters a list by source. Single select, clearable. |
| **SelectionBar** | Replaces the top bar during multi-select with count, share, move, delete. |
| **ViewerToolbar** | Auto-hides after reading begins; tap toggles. Holds back, title, find, overflow. |
| **PageScrubber** | Draggable page indicator with thumbnail preview. |
| **ConfirmSheet** | Bottom sheet for destructive actions, with Undo through a snackbar for deletes. |
| **PermissionCard** | Explains a missing permission and offers the exact action to fix it. |

6\. States and microcopy

| State | Treatment |
| --- | --- |
| **First scan in progress** | Skeleton rows and a progress line: "Found 1,240 documents so far". The list fills in as results arrive. |
| **Empty library** | "No documents yet. Files you receive or download will appear here." with an Add folder action. |
| **No search results** | "Nothing matches 'invoce'." with a spelling suggestion and the active filters shown as removable chips. |
| **Permission denied** | Cached library stays visible. Banner: "Allow access to find new documents." with one button to settings. |
| **Password needed** | Sheet with a password field, show-password toggle and "Open". |
| **Cannot open** | "This file can't be opened here." with Open with…, Share and file details. |
| **Deleted** | Snackbar "Moved to Trash" with Undo for 6 seconds. |

7\. Accessibility and localisation

- Every icon button has a content description; rows announce name, type, size and date in one sentence.
- File type is never signalled by colour alone; the type label is always present.
- Layouts hold at 200% font scale, and long names wrap instead of clipping.
- Dates and sizes follow the device locale; amounts in snippets are shown as written in the document.
- Translation strings avoid concatenation so Hausa, Yoruba and Igbo word order works.

RoadmapImplementation plan

# Docs Office: Roadmap

Phases, exit criteria and release milestones from first commit to a staged 1.0 launch.

Version 1.024 weeks to 1.0Estimate assumes one full-time Android engineer

1\. Phases

Phase 0 · Weeks 1 to 2

Foundations

- Repository, version catalog, convention plugins, CI with lint, detekt, unit tests and a size check.
- Design system module: theme, file-type chips, DocumentRow.
- Room schema v1, DataStore, Hilt wiring, navigation shell.
- Spike: scan 10,000 files on a low-end phone and record the time.
- Trademark search and a fallback app name.

Exit: app launches, requests access, scans a real phone and lists the result.

Phase 1 · Weeks 3 to 8

MVP library and PDF

- Home, Files, categories and sources, recents and favorites.
- File operations, trash, multi-select.
- Name search with filters.
- PDF, text and image viewers; open from other apps.
- Dark mode, settings, excluded folders.

Exit: Alpha to internal testers. All MVP requirements pass in the test plan on the device matrix.

Phase 2 · Weeks 9 to 15

Office viewers and content search

- DOCX, XLSX and PPTX readers against a growing test corpus.
- Background text extraction and FTS search with snippets.
- Tags, collections, bookmarks, reading progress, in-document find.
- Baseline Profile and the first performance pass.

Exit: Beta on Play closed testing with at least 50 real users; crash-free sessions at 99% or higher.

Phase 3 · Weeks 16 to 20

Scanner, tools and Pro

- Document scanner, review screen and OCR into searchable PDFs.
- Merge, split, reorder, rotate, compress, images to PDF.
- Play Billing, Pro entitlement and paywall.

Exit: purchase flow verified with test accounts; scans stay within memory budget on low-end devices.

Phase 4 · Weeks 21 to 24

Vault, polish, launch

- Encrypted vault with biometric and PIN.
- Hausa, Yoruba and Igbo translations reviewed by native speakers.
- Accessibility audit, final performance and size pass.
- Store listing, Data safety form, all-files access declaration, privacy policy.
- Staged production rollout.

Exit: 1.0 at 100% rollout with crash-free sessions at 99.5% or higher.

After 1.0

Version 2.0 candidates

- Optional encrypted cloud backup.
- PDF annotation, highlighting and signing.
- EPUB reader.
- Tablet and foldable layouts.

2\. Milestones

| Milestone | Target | Audience | Gate |
| --- | --- | --- | --- |
| **Alpha** | End of week 8 | Internal testing track | MVP requirements pass; no open crash on the matrix |
| **Beta** | End of week 15 | Closed testing, real users | Office viewers meet corpus pass rate; search within latency budget |
| **Release candidate** | Week 23 | Closed testing | Feature complete; zero severity-1 bugs; store assets approved |
| **1.0** | Week 24 | Production, staged | Rollout steps in the deployment plan |

3\. Dependencies and open decisions

| Item | Needed by | Notes |
| --- | --- | --- |
| **Final app name** | Week 2 | Trademark search and Play impersonation review for "Docs Office". |
| **Reference test devices** | Week 1 | At least one 2 GB Android Go phone and one mid-range phone. |
| **Test document corpus** | Week 3, grows after | Real-world PDFs, Word, Excel and PowerPoint files from many sources, including broken ones. |
| **Pricing and ads decision** | Week 14 | Price points and whether any ads ship in 1.0. |
| **Translators** | Week 18 | Native reviewers for Hausa, Yoruba and Igbo. |
| **Play developer account and policy forms** | Week 12 | All-files access declaration needs a demo video and a clear justification. |

**Schedule risk:** the Office viewers are the least predictable work. Keep a 20% buffer inside Phase 2, and if fidelity falls short, ship 1.0 with DOCX and XLSX and move PPTX to 1.1 rather than slipping the launch.

Test PlanQuality assurance

# Docs Office: Test Plan

How correctness, performance, security and usability are verified before each release.

Version 1.0Traces to: SRS v1.0

1\. Strategy

Most checks run automatically on every pull request. The risky parts of this app are file parsing and device variety, so the plan leans on a large document corpus and a real-device matrix with low-end phones, not on emulators alone.

| Level | Scope | Tools | When |
| --- | --- | --- | --- |
| **Unit** | Use cases, repositories, ViewModels, path classification, parsers | JUnit, MockK, Turbine, coroutines test | Every push |
| **Database** | DAOs, FTS queries, every migration | Room testing, in-memory database | Every push |
| **Parser corpus** | DOCX, XLSX, PPTX, PDF and text readers against real files | JVM tests over the corpus folder | Every push; full corpus nightly |
| **UI** | Screens and flows with fake repositories | Compose UI test | Every pull request |
| **Screenshot** | Light, dark, large font, long names | Roborazzi or Paparazzi | Every pull request |
| **Instrumented** | Storage access, file operations, intents, vault | AndroidX Test on emulators and Firebase Test Lab | Nightly, before release |
| **Performance** | Startup, scroll, open time, memory | Macrobenchmark, Perfetto traces | Weekly, before release |
| **Manual** | Exploratory, usability, accessibility | Device matrix, TalkBack | Each milestone |

2\. Document corpus

The corpus is a first-class test asset. It lives outside the app repository (large binaries) and is versioned separately.

- PDFs: scanned, text-heavy, very large page size, 500+ pages, password-protected, truncated, with unusual fonts.
- Word: styles and numbering, tables, embedded images, headers and footers, right-to-left text, text boxes, tracked changes.
- Excel: 100,000-row sheets, merged cells, custom number formats, hidden sheets, formulas with cached values, currency in naira.
- PowerPoint: grouped shapes, charts, SmartArt, embedded video placeholders, non-embedded fonts.
- Hostile files: zip bombs, deeply nested XML, entity expansion payloads, files with the wrong extension.
- Files produced by Microsoft Office, Google Docs, LibreOffice, WPS and mobile scanner apps, since each writes slightly different XML.

3\. Device matrix

| Tier | Example devices | Android | Purpose |
| --- | --- | --- | --- |
| **Low-end** | Android Go phones with 2 GB RAM, such as entry-level Tecno, Infinix and itel models | 10 to 13 | Memory limits, startup, battery; the reference for performance budgets |
| **Mid-range** | Samsung Galaxy A-series, Xiaomi Redmi | 12 to 15 | Main user profile |
| **High-end** | Pixel and Galaxy S-series | Latest | Newest APIs, large screens |
| **Oldest supported** | Emulator and one physical phone | 7.0 (API 24) | Legacy storage behaviour |

API levels covered in CI: 24, 29, 30, 33 and the current release. Storage behaviour changes at 29, 30 and 33, so each gets its own instrumented run.

4\. Critical test cases

| ID | Area | Scenario | Expected result |
| --- | --- | --- | --- |
| TC-DI-01 | Discovery | First launch on a phone with 10,000 files | Library fills progressively; UI stays responsive; scan finishes within 60 s |
| TC-DI-02 | Discovery | Receive a PDF in WhatsApp while the app is open | File appears under Recents and WhatsApp within 10 s |
| TC-DI-03 | Permissions | Revoke storage access while the app is in the background | On return, cached library shows read-only with a restore banner; no crash |
| TC-BO-01 | File actions | Move a file, then kill the app mid-copy | Original is intact; no partial file remains in the destination |
| TC-BO-02 | Trash | Delete, restore, delete again, advance clock 30 days | Restore returns the file to its folder; purge runs after 30 days |
| TC-VW-01 | PDF | Open a 500-page PDF and jump to page 400 | Page renders in under 1 s; memory stays under 96 MB |
| TC-VW-02 | PDF | Open an encrypted PDF with a wrong, then correct, password | Inline error, then success; password not written to disk or logs |
| TC-VW-03 | Word | Open a document with tables, lists and images | Structure matches the reference screenshots within agreed tolerance |
| TC-VW-04 | Excel | Open a 100,000-row sheet | First screen in under 2 s; scrolling at 60 fps; no out-of-memory |
| TC-VW-05 | Security | Open a zip bomb and an entity-expansion file | Rejected with the corrupt-file screen; memory and time stay bounded |
| TC-VW-06 | Intents | Tap a PDF in a chat and choose Docs Office | Opens directly in the viewer; back returns to the chat |
| TC-SR-01 | Search | Type three characters with 50,000 documents indexed | First results in 200 ms or less |
| TC-SC-01 | Scanner | Scan five pages, run OCR, search a word from page 4 | The PDF appears in results with the word highlighted |
| TC-VT-01 | Vault | Move a file to the vault, inspect storage with a file manager | No readable copy exists; file opens only after unlock |
| TC-PV-01 | Privacy | Run a full session behind a network proxy | Only Play services and the crash reporter are contacted; no document names in any request |

5\. Performance budgets

| Measure | Budget | Device |
| --- | --- | --- |
| **Cold start to Home** | 1.5 s or less | Low-end reference |
| **Open recent PDF (median)** | 1 s or less | Low-end reference |
| **Frame time while scrolling 5,000 rows** | 95% of frames under 16 ms | Mid-range |
| **Viewer peak memory** | 96 MB or less | Low-end reference |
| **Background indexing battery use** | Under 2% per hour of active work | Mid-range |
| **Bundle download size** | 25 MB or less | CI size report |

6\. Security, accessibility and usability checks

- **Security:** static analysis and dependency scanning in CI; manual review of every parser entry point; vault tested for key loss on biometric change and for plaintext residue in cache and temp folders.
- **Accessibility:** TalkBack pass on every screen, 200% font scale, contrast check on all themes, switch-access reachability for primary actions.
- **Usability:** five users per round on low-end phones with a task script: find a specific document, open a spreadsheet, scan and share a page. Measure time and errors.
- **Localisation:** pseudo-locale run to catch clipped text, plus native-speaker review of the three Nigerian languages.

7\. Release exit criteria

- All automated suites green; no skipped tests without a ticket.
- Zero open severity-1 bugs and no open data-loss bug of any severity.
- Parser corpus pass rate at or above the agreed threshold for each format.
- Performance budgets met on the low-end reference device.
- Closed beta crash-free sessions at 99% or higher for two consecutive weeks.

DeploymentBuild, release and operations

# Docs Office: Deployment Plan

Build variants, CI/CD, signing, Play Console setup, staged rollout and post-launch operations.

Version 1.0Distribution: Google Play (app bundle)

1\. Build variants

| Variant | Purpose | Settings |
| --- | --- | --- |
| **debug** | Daily development | Debuggable, application id suffix `.debug`, verbose logging, LeakCanary, no analytics |
| **staging** | Internal and QA builds | Release-like with R8 and a separate signing key; test billing products; crash reporting to a test project |
| **release** | Play Store | R8 full mode, resource shrinking, Baseline Profile, Play App Signing, production billing |

Version name follows `MAJOR.MINOR.PATCH`. Version code is computed as `MAJOR × 1,000,000 + MINOR × 10,000 + PATCH × 100 + build`, so it always increases and encodes the release.

2\. CI/CD pipeline

.github/workflows/android-ci.yml

name: android-ci on: \[push, pull_request\] jobs: verify: runs-on: ubuntu-latest steps: - uses: actions/checkout@v4 - uses: actions/setup-java@v4 with: { distribution: temurin, java-version: 17 } - uses: gradle/actions/setup-gradle@v4 - run: ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest - run: ./gradlew bundleStaging checkBundleSize release: if: startsWith(github.ref, 'refs/tags/v') needs: verify runs-on: ubuntu-latest steps: # decode keystore from a repository secret, build the release bundle, # upload to the Play internal track through the Play Developer API

1. Pull request: lint, static analysis, unit and UI tests, screenshot tests, bundle size check.
2. Merge to main: instrumented tests on emulators and the parser corpus run; a staging bundle is built and uploaded to the internal track.
3. Version tag: release bundle is built, signed and uploaded to the closed testing track for sign-off.
4. Promotion to production is a manual step in Play Console with a staged rollout.

**Secrets:** the upload key, keystore password and Play service account live only in the CI secret store. Play App Signing holds the app signing key, so a lost upload key can be reset without losing the app.

3\. Play Console setup

| Item | Detail |
| --- | --- |
| **App access and content** | No login required. Content rating questionnaire. Target audience set to adults and general users, not children. |
| **Data safety form** | Declare crash and diagnostic data if enabled and purchase data from Play Billing. State that documents are processed on the device and not collected. |
| **All-files access declaration** | Core purpose: document management and on-device search. Provide a short demo video showing discovery, the Files tab and the fallback without the permission. |
| **Privacy policy** | Public URL stating on-device processing, no document upload, what crash reporting collects, and how to contact the developer. |
| **Store listing** | Name, 80-character short description, full description, 4 to 8 phone screenshots (Home, Files, Viewer, Search, Scan), feature graphic and icon. |
| **Target API level** | Meet Play's current requirement at submission; verify the date before each release. |
| **Pricing and distribution** | Free with in-app Pro. Local pricing in naira and other currencies set in Play Console. |

4\. Release tracks and staged rollout

Internal testing

Team and trusted testers

Every merged build. Fast feedback; no review wait.

Closed testing

Beta users

Release candidates to a list of real users with a feedback channel. Run for at least two weeks before launch.

Production · 5%

First exposure

Hold for 48 hours. Watch crash-free sessions, ANR rate, startup time and reviews.

Production · 20% then 50%

Widen

Advance each step after 48 hours of healthy metrics.

Production · 100%

Full release

Announce, update the store listing and keep the previous build available for rollback.

**Halt rules:** pause the rollout if crash-free sessions fall below 99%, the ANR rate crosses Play's bad-behaviour threshold, any data-loss report is confirmed, or a privacy issue is found. Fix forward with a patch build; Play cannot roll users back to an older version.

5\. Release checklist

- All automated suites green on the release commit; corpus pass rate at threshold.
- Performance budgets verified on the low-end reference device.
- Bundle size at or under 25 MB; no debug libraries in the release build.
- Version name and code incremented; changelog written for each language.
- R8 mapping file uploaded to Play and Crashlytics.
- Data safety form, content rating and permission declaration match the build.
- Billing products active; purchase, restore and cancel flows verified on a test account.
- Privacy policy URL live and accurate.
- Screenshots and listing text current for this version.
- Rollback plan agreed: who watches metrics and who can halt the rollout.

6\. Monitoring and support

| Signal | Source | Response |
| --- | --- | --- |
| **Crashes and ANRs** | Play Console vitals, Crashlytics | Triage daily during rollout, weekly after. Severity-1 gets a patch within 72 hours. |
| **Startup and rendering time** | Play vitals, benchmark runs | Regression over 10% blocks the next release. |
| **Unreadable files** | Anonymous counts of open failures by format and error type | Add the failing pattern to the corpus and fix in the parser. |
| **Reviews and support email** | Play Console, support inbox | Reply within two working days; tag themes for the roadmap. |
| **Policy and API changes** | Play Console messages, Android release notes | Review each quarter; target API and storage policy are standing agenda items. |