import { initializeApp } from "firebase-admin/app";
import { getDatabase } from "firebase-admin/database";
import { DocumentReference, getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { onValueCreated } from "firebase-functions/v2/database";
import { logger } from "firebase-functions";

const DATABASE_URL = "https://omnistudio-caaf5-default-rtdb.firebaseio.com";
const DATABASE_INSTANCE = "omnistudio-caaf5-default-rtdb";
const TEST_ANDROID_PACKAGE = "com.aistudio.omnistudio.wkspea.test";
const PUSH_ENVIRONMENT = "test";
const MAX_TOKENS_PER_SEND = 500;

initializeApp({ databaseURL: DATABASE_URL });

interface ChatMember {
  email?: unknown;
}

interface DeviceToken {
  token: string;
  ref: DocumentReference;
}

function asText(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function normalizeEmail(value: unknown): string {
  return asText(value).toLowerCase();
}

function asMembers(value: unknown): ChatMember[] {
  if (Array.isArray(value)) return value.filter((item): item is ChatMember => !!item && typeof item === "object");
  if (value && typeof value === "object") {
    return Object.values(value as Record<string, unknown>)
      .filter((item): item is ChatMember => !!item && typeof item === "object");
  }
  return [];
}

function messagePreview(message: Record<string, unknown>): string {
  const text = asText(message.text);
  if (text) return text.slice(0, 1000);

  const mediaType = asText(message.mediaType).toLowerCase();
  if (mediaType === "image" || mediaType === "photo") return "Te enviaron una imagen";
  if (mediaType === "video") return "Te enviaron un video";
  if (mediaType === "audio" || mediaType === "voice") return "Te enviaron un audio";
  if (mediaType === "file" || mediaType === "document") return "Te enviaron un archivo";
  return "Nuevo mensaje";
}

async function getRecipientTokens(recipientEmails: string[]): Promise<DeviceToken[]> {
  const firestore = getFirestore();
  const byToken = new Map<string, DeviceToken>();

  // One equality query per email avoids a composite-index requirement. The appId
  // filter intentionally confines this function to the isolated .test APK.
  for (const email of recipientEmails) {
    const snapshot = await firestore.collection("fcm_device_tokens")
      .where("email", "==", email)
      .get();

    for (const doc of snapshot.docs) {
      const data = doc.data();
      const token = asText(data.fcmToken);
      if (!token || data.appId !== TEST_ANDROID_PACKAGE) continue;
      byToken.set(token, { token, ref: doc.ref });
    }
  }

  return [...byToken.values()];
}

export const notifyTestChatMessage = onValueCreated(
  {
    ref: "/chats/{channelId}/messages/{messageId}",
    instance: DATABASE_INSTANCE,
    region: "us-central1",
    maxInstances: 5,
  },
  async (event) => {
    const message = event.data.val() as Record<string, unknown> | null;
    if (!message || message.pushEnvironment !== PUSH_ENVIRONMENT) return;

    const channelId = asText(event.params.channelId);
    const senderEmail = normalizeEmail(message.senderEmail);
    if (!channelId || !senderEmail) {
      logger.warn("Skipping test chat push: missing channel or sender identity.");
      return;
    }

    const channelSnapshot = await getDatabase().ref(`chats/${channelId}`).get();
    const channel = channelSnapshot.val() as Record<string, unknown> | null;
    if (!channel) {
      logger.warn("Skipping test chat push: channel metadata is missing.", { channelId });
      return;
    }

    const recipientEmails = [...new Set(
      asMembers(channel.members)
        .map((member) => normalizeEmail(member.email))
        .filter((email) => email && email !== senderEmail),
    )];
    if (recipientEmails.length === 0) return;

    const devices = await getRecipientTokens(recipientEmails);
    if (devices.length === 0) {
      logger.info("No registered .test devices for this chat message.", { channelId });
      return;
    }

    const isGroup = channel.isGroup === true;
    const data = {
      channelId,
      channelName: asText(channel.name) || "Chat",
      senderName: asText(message.senderName) || senderEmail,
      senderEmail,
      text: messagePreview(message),
      isGroup: String(isGroup),
      messageId: asText(message.messageId) || asText(event.params.messageId),
      pushEnvironment: PUSH_ENVIRONMENT,
    };

    for (let start = 0; start < devices.length; start += MAX_TOKENS_PER_SEND) {
      const batch = devices.slice(start, start + MAX_TOKENS_PER_SEND);
      const result = await getMessaging().sendEachForMulticast({
        tokens: batch.map((device) => device.token),
        data,
        android: {
          priority: "high",
          collapseKey: `chat_${channelId}`.slice(0, 64),
        },
      });

      const staleTokenDeletes: Promise<unknown>[] = [];
      result.responses.forEach((response, index) => {
        if (response.success) return;
        const code = response.error?.code;
        if (code === "messaging/registration-token-not-registered" || code === "messaging/invalid-registration-token") {
          staleTokenDeletes.push(batch[index].ref.delete());
        }
      });
      await Promise.all(staleTokenDeletes);
    }

    logger.info("Test chat push sent.", {
      channelId,
      recipients: recipientEmails.length,
      devices: devices.length,
      isGroup,
    });
  },
);
