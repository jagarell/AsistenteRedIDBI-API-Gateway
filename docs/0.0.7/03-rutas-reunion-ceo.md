# Rutas de la reunión con el CEO

| Método y ruta | Roles |
|---|---|
| `GET /bitacora` | administrador (todo) · técnico y supervisor (por minuta propia o de su equipo) |
| `POST /minutas/{id}/archivar` · `POST /minutas/{id}/restaurar` | técnico (archivar sus borradores) · administrador (restaurar) |
| `GET /minutas/por-validar?orden=prioridad` | supervisor, administrador |
| `POST /sync` | técnico, supervisor, administrador |
| `GET/PUT /empresa/copia-seguridad` · `POST /copias/ahora` · `GET /copias` | administrador |
| `PATCH /evaluaciones/{id}/respuestas/{nodo}` | técnico (dueño) |

- `POST /sync` acepta lotes de hasta 200 cambios y responde 207 con el resultado por cambio.
- Todas las rutas escriben en la bitácora a través de la API.


## Pasar a producción
- `POST /api/empresa/produccion` → API `POST /empresa/produccion`. Solo token con rol ADMINISTRADOR (si no, `403`). Respuesta `200 { "produccion_desde": "…" }` o `409` si ya está en producción.
