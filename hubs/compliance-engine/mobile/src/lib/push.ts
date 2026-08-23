import { Capacitor } from '@capacitor/core';
import { PushNotifications } from '@capacitor/push-notifications';
import { mobileApi } from './api';

// L'API Push Notifications de Capacitor n'a pas d'implementation web utile
// (pas de FCM/APNs dans un navigateur) -- on ne l'active que sur iOS/Android.
export async function registerPushNotifications() {
  if (!Capacitor.isNativePlatform()) return;

  const permission = await PushNotifications.checkPermissions();
  if (permission.receive !== 'granted') {
    const requested = await PushNotifications.requestPermissions();
    if (requested.receive !== 'granted') return;
  }

  await PushNotifications.register();

  PushNotifications.addListener('registration', async (token) => {
    try {
      const platform = Capacitor.getPlatform() === 'ios' ? 'ios' : 'android';
      await mobileApi.device.register(token.value, platform);
    } catch (e) {
      console.warn('[Push] Échec d\'enregistrement du device', e);
    }
  });

  PushNotifications.addListener('registrationError', (err) => {
    console.warn('[Push] Erreur d\'enregistrement', err);
  });
}
