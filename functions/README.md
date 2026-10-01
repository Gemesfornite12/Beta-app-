# Push de chat para OmniStudio `.test`

Esta función solo procesa mensajes que llevan `pushEnvironment: "test"` y solo busca dispositivos registrados por el paquete Android `com.aistudio.omnistudio.wkspea.test`. Los mensajes de la app pública quedan fuera.

## Requisitos

- Proyecto Firebase `omnistudio-caaf5` con Realtime Database, Firestore y Firebase Cloud Messaging.
- Node.js 20 y Firebase CLI para preparar y desplegar Functions.
- El despliegue de Cloud Functions puede requerir habilitar el plan Blaze y una cuenta de facturación. No hay claves privadas ni credenciales de servicio en este directorio.
- Cada usuario de prueba debe instalar la APK `.test`, iniciar sesión y permitir notificaciones. Los interruptores por chat siguen aplicándose localmente en el dispositivo.

La función de este directorio **no se despliega automáticamente**. No agregues el proyecto Firebase por defecto ni ejecutes un despliegue sin autorización: al activarla, enviará avisos a los miembros de prueba cuando se guarden nuevos mensajes.
