# Changelog

## [0.0.7] - 2026-11-02 (propuesta, sin publicar)
### Agregado
- **Asistente IA** (módulo nuevo): conversación libre por texto o audio, sin botones de opciones. Redacta minutas con evidencias y contexto, actualiza minutas generadas, responde consultas sobre la base de minutas y da reportes. Responde y analiza con **ChatGPT (API de OpenAI)** usando como contexto las reglas de la app y las minutas.
- Conexiones de red contadas por audio o texto que se dibujan en la topología.
- Ubicación **GPS** con la dirección: enlace al mapa, dirección encontrada, distrito y departamento, y coincidencia con el nombre del local.
- **Empresa configurable**: logo y nombre (IDBI por defecto). El nombre del **sistema de impresiones** (Wrapper en IDBI) se define en Preguntas y reglas.
- Recuperación de contraseña con **aprobación del administrador** (elige el número que ve el usuario).
- Gestión desde el asistente: minutas por validar, pendientes del día, reportes por periodo y reportes programados (por ejemplo, cada fin de mes).
- **Ayuda con ChatGPT y fotos**: preguntas libres y foto del equipo; la IA dice marca, modelo y si el switch es administrable.
- **Evidencias flexibles**: el técnico toma la foto y dice a qué pertenece; por impresora solo el selftest es obligatorio; la IA ordena el anexo y completa datos desde la imagen.
- **Ubicar puerto del switch** sin desconectar cables: la app hace parpadear la luz del puerto de cada impresora (cualquier switch) o lee la tabla de MAC del switch (administrable), y mide la calidad de cada punto de red.
- Pregunta de **rotulado** de los puntos en el switch y **nombres de zonas** definidos una sola vez.
- **Reglas de la revisión de la minuta de El Chinito**: topología y métodos de pago solo si se consideran; nombres simples; equipos apagados no son «con falla»; solo equipos de la implementación (más repetidores, AP y cámaras); IP fijas de las impresoras; reserva de IP con el proveedor; lo resuelto en la visita va en Pasos a seguir y sube el porcentaje; UPS solo con intermitencias de energía; tabla de impresoras hacia el switch bajo consulta; fotos de documentos transcritas con consulta; ubicación exacta con observación.
- **Reunión con el CEO (23/10/2026)**: bitácora de trazabilidad y minutas archivadas (nada se borra); «Revisar primero» para el supervisor (alertas y menor porcentaje); «No se pudo mapear la red» con aviso solo en la notificación; un solo botón de compartir; «¿qué me falta?» en el Asistente IA; modo sin conexión (offline-first) con cola de sincronización; copias de seguridad automáticas al correo de la empresa con frecuencia configurable; versión web del Chat IA y del Asistente IA con la misma cuenta y datos que la app.
- **Nodos que no traban**: editar una respuesta y seguir donde estabas; nota opcional en cada evidencia; foto dudosa «Guardar como correcta»; iMin/Sunmi con o sin impresora integrada (foto si no sabe el modelo); AnyDesk opcional, salvo el del equipo con el sistema de impresiones, que se pide antes de la minuta con opción de no considerarlo.
### Cambiado
- **Código del Gateway (Parte 2)**: rol ADMINISTRADOR, Roles y permisos, Reportes por rol con filtros e indicadores, actualizar minutas con el Asistente IA (versiones e historial), minuta de soporte, aprendizaje del día, reglas y preguntas aprobadas, fichas de conocimiento. Detalle en docs/0.0.7/04-codigo-0.0.5-a-0.0.7.md y migración en docs/0.0.7/migracion-0.0.7.sql.
- Roles: permisos nuevos r_tec_all (Por técnico) y r_tec_mine (Mis técnicos) para Reportes.

- Pasar a producción (administrador, irreversible): reportes e indicadores en 0, minutas demo conservadas (Completa, En revisión, Validada) sin contar, base de conocimiento de la IA intacta.

- El chat guiado de la evaluación se llama **Chat IA** (antes «Asistente IA»).
- Estado final: solo el equipo con el sistema de impresiones por WiFi es observación; PC, laptop o tablet sin el sistema por WiFi no impide iniciar la operación. Las observaciones resueltas en la visita dejan el local **APTO** y el resumen lo menciona.
- Minuta: sin el texto «generada por el Asistente Virtual de Red», con logo y nombre de la empresa, sin la fila de correo si no hay correo, «Distrito / Departamento» en lugar de «Ciudad», anexo de evidencias agrupado y compacto, historial de actualizaciones y fecha de la nueva visita cuando corresponde.
### Quitado
- «Crear cuenta nueva» en la app: las cuentas las crea el administrador desde la web.
