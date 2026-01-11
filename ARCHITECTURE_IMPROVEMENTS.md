# Plan de Mejoras de Arquitectura - Claude Monitor Android

## Estado Actual
- ✅ **1. Error Handler Centralizado** - Implementado

## Mejoras Pendientes

---

### 2. Retry & Timeout Policies
**Objetivo:** Políticas configurables de reintentos con backoff exponencial.

**Archivos a crear:**
```
core/network/
├── RetryPolicy.kt        # Configuración de reintentos
├── NetworkConfig.kt      # Timeouts y configuración global
└── RetryInterceptor.kt   # OkHttp interceptor con retry
```

**Funcionalidades:**
- Backoff exponencial (1s → 2s → 4s → max 10s)
- Máximo 3 intentos por defecto
- Configurable por tipo de request
- Circuit breaker para evitar sobrecarga

---

### 3. Connectivity Observer
**Objetivo:** Observar estado de conectividad en tiempo real.

**Archivos a crear:**
```
core/network/
└── ConnectivityObserver.kt  # Flow de estado de red
```

**Funcionalidades:**
- Estados: Available, Unavailable, Losing, Lost
- Auto-retry cuando la red vuelve
- Banners de "Sin conexión" automáticos
- Integración con WebSocket reconnect

---

### 4. WebSocket Reconnection Strategy
**Objetivo:** Reconexión automática inteligente para terminales.

**Archivos a modificar/crear:**
```
data/api/
├── WebSocketManager.kt      # Manager con reconnect strategy
└── WebSocketService.kt      # (modificar existente)
```

**Funcionalidades:**
- Auto-reconnect con backoff
- Mantener buffer durante desconexión
- Reconnect cuando hay red disponible
- Estados: Disconnected → Connecting → Connected → Reconnecting

---

### 5. UseCase Layer (Clean Architecture)
**Objetivo:** Capa de casos de uso entre ViewModel y Repository.

**Archivos a crear:**
```
domain/
├── usecase/
│   ├── base/
│   │   ├── UseCase.kt           # Base class
│   │   └── FlowUseCase.kt       # Para Flows
│   ├── driver/
│   │   ├── GetDriversUseCase.kt
│   │   ├── AddDriverUseCase.kt
│   │   └── CheckConnectionUseCase.kt
│   ├── project/
│   │   └── GetProjectsUseCase.kt
│   ├── session/
│   │   ├── GetSessionsUseCase.kt
│   │   └── ResumeSessionUseCase.kt
│   └── terminal/
│       ├── GetTerminalsUseCase.kt
│       ├── CreateTerminalUseCase.kt
│       └── ConnectTerminalUseCase.kt
└── model/
    └── (domain models si difieren de data models)
```

**Beneficios:**
- Lógica de negocio aislada
- Fácil testing
- ViewModels más limpios
- Reutilización entre pantallas

---

### 6. UI State Unificado
**Objetivo:** Base state y componentes reutilizables para todas las pantallas.

**Archivos a crear:**
```
ui/base/
├── BaseUiState.kt           # Interface común
├── BaseViewModel.kt         # ViewModel base con error handling
└── BaseScreen.kt            # Composable wrapper con estados comunes

ui/components/
├── StateContainer.kt        # Loading/Error/Content wrapper
└── PullToRefresh.kt         # Pull to refresh unificado
```

**Funcionalidades:**
- Estados comunes (loading, error, empty, content)
- Pull to refresh integrado
- Manejo de errores consistente
- Animaciones de transición

---

### 7. Logging & Analytics
**Objetivo:** Sistema de logging estructurado y analytics.

**Archivos a crear:**
```
core/logging/
├── AppLogger.kt             # Interface de logging
├── LoggerImpl.kt            # Implementación (Logcat + archivo)
├── CrashReporter.kt         # Interface para crash reporting
└── Analytics.kt             # Interface para analytics

di/
└── LoggingModule.kt         # Hilt module
```

**Funcionalidades:**
- Niveles: Debug, Info, Warning, Error
- Logging a archivo para debug
- Preparado para Crashlytics/Firebase
- Analytics de uso (pantallas, acciones)

---

### 8. Testing Infrastructure
**Objetivo:** Base para testing unitario e instrumental.

**Archivos a crear:**
```
app/src/test/
├── core/error/
│   ├── ErrorHandlerTest.kt
│   └── ResourceTest.kt
├── data/repository/
│   └── DriverRepositoryTest.kt
└── domain/usecase/
    └── GetDriversUseCaseTest.kt

app/src/androidTest/
└── ui/screens/
    └── DriversScreenTest.kt
```

---

## Orden de Implementación Recomendado

```
Fase 1: Core Infrastructure
├── 2. Retry & Timeout Policies
├── 3. Connectivity Observer
└── 7. Logging & Analytics

Fase 2: Architecture Refactor
├── 5. UseCase Layer
└── 6. UI State Unificado

Fase 3: Features
└── 4. WebSocket Reconnection Strategy

Fase 4: Quality
└── 8. Testing Infrastructure
```

## Estimación de Archivos

| Mejora | Archivos Nuevos | Archivos Modificados |
|--------|-----------------|---------------------|
| Retry & Timeout | 3 | 2 |
| Connectivity | 1 | 3 |
| WebSocket Reconnect | 1 | 1 |
| UseCase Layer | 12 | 5 |
| UI State Unificado | 4 | 6 |
| Logging | 4 | 2 |
| Testing | 5 | 0 |
| **Total** | **30** | **19** |

---

## Diagrama de Arquitectura Final

```
┌─────────────────────────────────────────────────────────────────┐
│                         UI LAYER                                 │
│  BaseScreen + StateContainer + ErrorComponents                  │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐   │
│  │ Drivers │ │Projects │ │Sessions │ │Terminals│ │Terminal │   │
│  └────┬────┘ └────┬────┘ └────┬────┘ └────┬────┘ └────┬────┘   │
└───────┼──────────┼──────────┼──────────┼──────────┼─────────────┘
        │          │          │          │          │
        ▼          ▼          ▼          ▼          ▼
┌─────────────────────────────────────────────────────────────────┐
│                      VIEWMODEL LAYER                             │
│  BaseViewModel + UiState<T> + Resource<T>                       │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                       DOMAIN LAYER                               │
│  UseCases (GetDrivers, AddDriver, GetProjects, etc.)            │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                        DATA LAYER                                │
│  Repositories + DataSources                                      │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────────────────┐ │
│  │    Remote    │ │    Local     │ │       WebSocket          │ │
│  │  DataSource  │ │  DataSource  │ │    (with reconnect)      │ │
│  └──────────────┘ └──────────────┘ └──────────────────────────┘ │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                        CORE LAYER                                │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌──────────────┐  │
│  │ErrorHandler│ │   Retry    │ │Connectivity│ │   Logger     │  │
│  │            │ │   Policy   │ │  Observer  │ │              │  │
│  └────────────┘ └────────────┘ └────────────┘ └──────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```
