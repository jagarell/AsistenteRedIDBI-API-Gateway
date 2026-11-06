# Recuperación de contraseña con aprobación del administrador

## Flujo
1. `POST /auth/recuperacion` `{ "correo": "tecnico@empresa.pe" }`
   → `{ "solicitud_id": "uuid", "numero": 59, "expira_en_s": 600 }`
   - Genera un número de 2 dígitos y dos números distintos de distracción.
   - Envía al administrador de la empresa una notificación push y una en Notificaciones, con los 3 números en orden aleatorio.
   - Responde igual aunque el correo no exista (no revela si existe); en ese caso no se notifica a nadie.
2. Mientras espera, la app consulta `GET /auth/recuperacion/{solicitud_id}` → `{ "estado": "esperando | aprobada | rechazada | expirada", "motivo": "numero | no_reconoce" }`.
3. El administrador responde: `POST /auth/recuperacion/{solicitud_id}/respuesta` `{ "numero": 59 }` o `{ "rechazar": true }`.
   - Número correcto → `aprobada` y se emite un token de un solo uso para cambiar la contraseña (10 minutos).
   - Número incorrecto o rechazo → `rechazada`.
4. `POST /auth/recuperacion/{solicitud_id}/nueva-contrasena` `{ "token": "...", "contrasena": "********" }` (mínimo 8 caracteres) → 200.

## Seguridad
- Una solicitud vence a los 10 minutos.
- Máximo 3 solicitudes por correo por hora.
- Se registra quién aprobó o rechazó y cuándo.

## Criterios de aceptación
- Dada una solicitud con número 59, cuando el administrador envía 59, entonces el estado es `aprobada`.
- Cuando envía 71, entonces el estado es `rechazada` con motivo `numero`.
- Dada una solicitud de hace 11 minutos, cuando se consulta, entonces el estado es `expirada`.


## Cambio del 03/11: quién ve el código
- El **código** lo ve el **administrador** (`GET /api/auth/recuperacion/{id}/codigo`, solo rol ADMINISTRADOR).
- El **usuario** recibe `opciones` (3 números) y responde con `POST /api/auth/recuperacion/{id}/respuesta {"numero": 39}`. Sigue el límite de 3 solicitudes por hora y la expiración de 10 minutos.
