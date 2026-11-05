# Código del Gateway: de 0.0.5 a 0.0.7 (Parte 2)

Punto de partida: `main` (commit `9f45d13`, 0.0.5). Versión del proyecto: `0.0.7` (pom.xml).
**Sin compilar en el entorno donde se generó** (no había acceso a Maven Central): correr `./mvnw test` antes del PR.
Necesita el API 0.0.7 (Parte 1) porque llama a sus rutas nuevas.

## Archivos cambiados
| Archivo | Cambio |
|---|---|
| `auth/Role.java` | Rol `ADMINISTRADOR` (ver `migracion-0.0.7.sql`). |
| `security/AuthenticatedUser.java` | `isAdmin()`. |
| `auth/UserEntity.java` | `supervisorId`: supervisor a cargo del técnico (Mis técnicos). |
| `minuta/Minuta.java` | `tipo` (IMPLEMENTACION/SOPORTE), `version`, `rubro`, `puntaje`, `esfuerzo`, `minutosElaboracion`, `accesosJson`, `soporteJson`, `estadoSoporte`. |
| `minuta/dto/MinutaResponse.java` | Devuelve `tipo`, `version`, `rubro`, `puntaje`, `estadoSoporte`. |
| `minuta/MinutaController.java` | Validar y devolver: supervisor **o administrador**. |
| `pdf/MinutaPdfService.java` | Al pedir el documento al API agrega `edits.cred` (Accesos y contraseñas) y la ubicación del router aplicada por el Asistente IA. |

## Archivos nuevos
| Paquete | Rutas | Qué hace |
|---|---|---|
| `common/FastApiClient` | — | Llamadas al API (FastAPI). |
| `roles/` | `GET/POST /api/roles`, `PUT /api/roles/{id}` (administrador), `GET /api/roles/mi-perfil-reportes` | Roles y permisos con los roles base; casillas bloqueadas; Por técnico (`r_tec_all`) y Mis técnicos (`r_tec_mine`) excluyentes; perfil de Reportes: General = `m_all`, Por técnico / Mis técnicos, Mis minutas = `a_create`. |
| `reportes/` | `GET /api/reportes?vista=general|tecnicos|mis&desde=&hasta=&tecnico=&supervisor=&rubro=&estado=&criticidad=&esfuerzo=&plazo=` | Pestañas por permisos (403 si no le toca); filtros que recalculan la lista y los 4 indicadores (completadas, a tiempo, tiempo promedio, en riesgo); por técnico: avance, a tiempo, tiempo promedio, en mora, devueltas; `plazosEmpresa` solo para el administrador. Plazo por nivel de esfuerzo (Básica mismo día, Intermedia 1 día, Compleja 2 días). |
| `minuta/MinutaChanges*`, `MinutaVersion` | `POST /api/minutas/{id}/cambios/plan`, `POST /api/minutas/{id}/cambios/aplicar`, `GET /api/minutas/{id}/versiones` | Actualizar una minuta con el Asistente IA: contraseñas, ubicación del router, puertos del switch, recomendaciones, pasos y observaciones; sube de versión con historial y, si estaba Completa o Validada, vuelve a Borrador (validación del supervisor). |
| `soporte/` | `GET /api/clientes/antecedentes?cliente=`, `POST /api/asistente/soporte/evaluar`, `POST /api/minutas/soporte` | Minuta de soporte: antecedentes del cliente, reglas S1–S4 (las calcula el API), documento y guardado como minuta `SOPORTE` en Pendiente. |
| `conocimiento/` | `POST /api/asistente/mensaje`, `GET /api/aprendizaje/hoy`, `POST /api/aprendizaje/aplicar`, `POST /api/conocimiento/fichas` | «¿Qué aprendiste hoy?» (solo administrador) con las visitas del día; «procede» guarda reglas (`reglas_empresa`, activas) y preguntas (`preguntas_flujo`) con «aprobada por el administrador»; fichas (`fichas_conocimiento`) y PDF. Reglas base cargadas por `ReglasSeeder` (R60 desactivada). El mensaje libre manda al API las reglas activas y la base de conocimiento como contexto. |

## Pruebas nuevas
`roles/RoleServiceTest`, `reportes/ReportesServiceTest`, `minuta/MinutaChangesServiceTest`.

## Pendiente para versiones siguientes (no está en este paquete)
Recuperación de contraseña con aprobación del administrador, alta de usuarios desde la web, Panel e indicadores del Inicio, bitácora completa, sincronización offline, copias de seguridad, reportes programados y empresa configurable (logo, nombre, sistema de impresiones). Están descritos en los demás documentos de `docs/0.0.7/`.
