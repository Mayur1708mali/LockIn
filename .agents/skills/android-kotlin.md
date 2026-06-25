---
name: android-kotlin
description: Android + Kotlin best practices for Jetpack Compose, MVVM Clean Architecture, Room, Hilt, and VpnService. Load this skill whenever writing or reviewing Kotlin/Android code for the LockIn project.
---

# Android Kotlin Skill

## Architecture Rules
- MVVM + Clean Architecture strictly
- Layer order: UI → ViewModel → UseCase → Repository → DataSource
- No layer skipping — ViewModel never touches DAO or Retrofit directly
- Domain layer (UseCase, Repository interfaces, Models) has zero Android dependencies
- Use cases are single-function: `operator fun invoke()`

## Jetpack Compose Rules
- No XML layouts anywhere — Compose only
- All colors via `MaterialTheme.colorScheme` — never hardcoded
- All strings via `stringResource()` — never hardcoded
- Stateless composables preferred — hoist state to ViewModel
- Use `LaunchedEffect`, `SideEffect`, `DisposableEffect` correctly — never launch coroutines in composable body
- Preview every composable with `@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)`

## State Management
- ViewModel exposes `StateFlow<UiState>` — never `MutableStateFlow` directly
- Sealed class for UI state:
```kotlin
sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}
```
- Collect state in Compose with `collectAsStateWithLifecycle()` — not `collectAsState()`

## Coroutines
- All coroutines in `viewModelScope` or `lifecycleScope`
- Always use structured concurrency — never `GlobalScope`
- Always handle exceptions with `try/catch` or `CoroutineExceptionHandler`
- Use `Dispatchers.IO` for DB and network, `Dispatchers.Main` for UI

## Room DB Rules
- Entity fields use primitive types — avoid nullable where possible
- Always use `@TypeConverter` for enums and complex types
- DAOs return `Flow<T>` for reactive queries, `suspend fun` for mutations
- Database version must be incremented and migration provided on schema change

## Hilt DI Rules
- `@HiltViewModel` on all ViewModels
- `@Singleton` for repositories and database
- `@Module` + `@InstallIn` for all Hilt modules
- Never use `Hilt` to inject into Services directly — use `@AndroidEntryPoint`

## VpnService Rules
- Always call `establish()` in a try/catch — it can return null
- Store `ParcelFileDescriptor` reference and close it properly on stop
- Use `addDisallowedApplication()` for allowlisting (MVP approach)
- Always run as foreground service with a visible notification
- Register in manifest with `<intent-filter><action android:name="android.net.VpnService"/></intent-filter>`

## Naming Conventions
- Files: `PascalCase.kt`
- Functions: `camelCase`
- Constants: `SCREAMING_SNAKE_CASE` in companion object
- Composables: `PascalCase` with no `()` suffix in name
- Use cases: verb + noun — `StartSessionUseCase`, `GetWalletUseCase`

## Logging
- Always use `Timber.d()`, `Timber.e()`, `Timber.w()` — never `Log.*`
- Initialize Timber in `Application.onCreate()` for debug builds only
