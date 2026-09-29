# Conectores de Google Workspace de Groq en OmniStudio

OmniStudio ofrece los conectores beta de Google Workspace de Groq como fuentes individuales de solo lectura: Gmail, Google Calendar y Google Drive. Cada usuario decide cuáles autorizar y cuáles usar en sus consultas de Sara.

## Configuración de Google Cloud

1. Abre el proyecto de Google Cloud usado por OmniStudio (`omnistudio-caaf5`) en [Google Auth Platform](https://console.developers.google.com/auth/overview). Completa el nombre, logo, correo de soporte, página principal, política de privacidad y términos del servicio.
2. Habilita Gmail API, Google Calendar API y Google Drive API.
3. En **Data Access**, agrega Gmail y Drive en modo de solo lectura y Calendar con el scope de solo lectura indicado abajo:
   - Gmail: `https://www.googleapis.com/auth/gmail.readonly`
   - Calendar: `https://www.googleapis.com/auth/calendar.events.readonly` (mantiene el permiso limitado a consulta)
   - Drive: `https://www.googleapis.com/auth/drive.readonly`
4. En **Clients**, crea o confirma un cliente OAuth Android con el paquete `com.aistudio.omnistudio.wkspea`. Registra el SHA-1 del certificado debug para probar y el SHA-1 de Play App Signing para la app distribuida.
5. Mantén la pantalla de consentimiento en **Testing** durante las pruebas y agrega como usuarios de prueba las cuentas Google que usarán la función.
6. Antes de ofrecer Gmail y Drive a usuarios externos, completa la verificación de Google para los scopes sensibles. Google documenta que `calendar.events` permite ver y editar eventos, mientras que `calendar.events.readonly` solo permite verlos. OmniStudio solicita el scope más limitado. La documentación actual del conector de Groq lista `calendar.events`, así que hay que validar en una prueba real si acepta el scope read-only; si lo rechaza, Calendar queda desactivado y no se amplía el permiso sin aprobación explícita.

La sesión Firebase/Google existente identifica a la persona, pero no concede acceso a Gmail, Calendar o Drive. La app solicita esos permisos aparte mediante Google Identity `AuthorizationClient`, únicamente cuando se conecta o selecciona un servicio. Los tokens de acceso son temporales; no se guardan en el APK, las preferencias ni el servidor. El gateway los usa solo durante esa consulta y no los registra.

## Uso y privacidad

- Los IDs son `connector_gmail`, `connector_googlecalendar` y `connector_googledrive`.
- Solo se envían a Groq los conectores que el usuario haya seleccionado para usar con Sara.
- La integración expone las funciones de lectura/búsqueda; no envía ni modifica correos, eventos o archivos.
- La respuesta se guarda en el chat privado de Sara en Firebase, como las demás respuestas del chat.
- Quitar un conector en OmniStudio detiene su uso desde la app; el permiso de Google también se puede revocar desde [Cuenta de Google > Conexiones](https://myaccount.google.com/connections).
- El gateway usa `POST /api/rasa/groq/workspace` y Groq Responses API. La clave de Groq permanece en el servidor.

Documentación: [conectores MCP de Groq](https://console.groq.com/docs/tool-use/remote-mcp/connectors) y [autorización de Google en Android](https://developer.android.com/identity/authorization).
