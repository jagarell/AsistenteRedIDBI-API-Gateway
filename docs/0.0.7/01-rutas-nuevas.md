# Rutas nuevas y permisos

| Método y ruta | Destino | Roles |
|---|---|---|
| `POST /asistente/mensaje` · `POST /asistente/mensaje/stream` | API FastAPI | técnico, supervisor, administrador |
| `POST /asistente/minutas/{id}/aplicar-actualizacion` | API FastAPI | técnico (dueño), administrador |
| `POST /minutas/{id}/resueltas` · `DELETE /minutas/{id}/resueltas/{rid}` | API FastAPI | técnico (dueño), administrador |
| `POST /minutas/{id}/solicitar-validacion` | API FastAPI | técnico (dueño) |
| `POST /geo/reverse` | API FastAPI | técnico, administrador |
| `GET /reportes/minutas` | API FastAPI | todos (alcance por rol) |
| `GET/POST/DELETE /reportes/programados` | API FastAPI | todos (cada uno los suyos) |
| `GET/PUT /empresa` (logo, nombre) | API FastAPI | GET todos · PUT administrador |
| `PUT /empresa/sistema-impresiones` | API FastAPI | administrador |

- El gateway agrega a cada llamada: `usuario_id`, `rol`, `empresa_id`. La API filtra los datos con ellos.
- Límite de uso del asistente por usuario (por ejemplo, 30 mensajes por minuto) para cuidar el costo de OpenAI.
- `POST /auth/register` deja de estar disponible para la app (solo el panel web del administrador crea usuarios).

## Criterios de aceptación
- Dado un técnico, cuando llama a `PUT /empresa`, entonces recibe 403.
- Dado un supervisor, cuando llama a `GET /reportes/minutas`, entonces solo recibe minutas de su equipo.
