export interface NotificationDestination { recipientId: string; contactId?: string; channelId?: number }

const identity = /^usr_[1-9][0-9]*_[A-Za-z0-9_-]{22}$/;

export function notificationDestination(search: string): NotificationDestination | null {
  const parameters = new URLSearchParams(search);
  const recipientId = parameters.get('recipient') ?? '';
  if (!identity.test(recipientId)) return null;
  const contactId = parameters.get('contact') ?? '';
  if (parameters.get('notification') === 'direct' && identity.test(contactId)) return { recipientId, contactId };
  const channel = parameters.get('channel') ?? '';
  const channelId = Number(channel);
  if (parameters.get('notification') === 'channel' && /^[1-9][0-9]*$/.test(channel) && Number.isSafeInteger(channelId)) return { recipientId, channelId };
  return null;
}

export function clearNotificationDestination() {
  const url = new URL(window.location.href);
  for (const key of ['notification', 'recipient', 'contact', 'channel']) url.searchParams.delete(key);
  window.history.replaceState(window.history.state, '', url);
}
