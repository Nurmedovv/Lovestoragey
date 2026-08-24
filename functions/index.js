const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

exports.onMessageSent = functions
    .region("europe-central2")
    .firestore.document("couples/{coupleId}")
    .onUpdate(async (change, context) => {
      const before = change.before.data();
      const after = change.after.data();

      // пропускаем если ничего не изменилось
      if (before.message_text === after.message_text &&
          before.message_sender === after.message_sender &&
          before.message_timestamp === after.message_timestamp) {
        return null;
      }

      const messageText = after.message_text;
      const senderUid = after.message_sender;

      if (!messageText || !senderUid) {
        return null;
      }

      const partner1 = after.partner1;
      const partner2 = after.partner2;

      if (!partner2) {
        console.log("Партнёр не привязан");
        return null;
      }

      const partnerUid = senderUid === partner1 ? partner2 : partner1;

      if (!partnerUid) {
        return null;
      }

      const tokensMap = after.fcm_tokens || {};
      const partnerToken = tokensMap[partnerUid];

      if (!partnerToken) {
        console.log("Нет FCM-токена для: " + partnerUid);
        return null;
      }

      const namesMap = after.names || {};
      const senderName = namesMap[senderUid] || "Партнёр";

      const payload = {
        notification: {
          title: senderName,
          body: messageText,
        },
        token: partnerToken,
      };

      try {
        const response = await admin.messaging().send(payload);
        console.log("Push отправлен: " + response);
      } catch (error) {
        console.error("Ошибка push: " + error.message);

        // если токен невалидный — удаляем его из Firestore
        if (error.code === "messaging/registration-token-not-registered" ||
            error.code === "messaging/invalid-registration-token") {
          console.log("Удаляем невалидный токен для: " + partnerUid);
          await change.after.ref.update({
            ["fcm_tokens." + partnerUid]: admin.firestore.FieldValue.delete()
          });
        }
      }

      return null;
    });
