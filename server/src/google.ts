import { OAuth2Client } from 'google-auth-library'
import { google, type androidpublisher_v3 } from 'googleapis'
import { env } from './env.js'

const oauth = new OAuth2Client()

export type GoogleUser = { sub: string; email: string; name?: string; picture?: string }

export async function verifyGoogleIdToken(idToken: string): Promise<GoogleUser> {
  const ticket = await oauth.verifyIdToken({
    idToken,
    audience: env.GOOGLE_WEB_CLIENT_ID,
  })
  const payload = ticket.getPayload()
  if (!payload?.sub || !payload.email) {
    throw new Error('Invalid Google ID token payload')
  }
  if (!payload.email_verified) {
    throw new Error('Email not verified')
  }
  return {
    sub: payload.sub,
    email: payload.email,
    name: payload.name,
    picture: payload.picture,
  }
}

let cachedPublisher: androidpublisher_v3.Androidpublisher | null = null

function publisher(): androidpublisher_v3.Androidpublisher {
  if (cachedPublisher) return cachedPublisher
  const auth = new google.auth.GoogleAuth({
    keyFile: env.GOOGLE_SERVICE_ACCOUNT_JSON,
    scopes: ['https://www.googleapis.com/auth/androidpublisher'],
  })
  cachedPublisher = google.androidpublisher({ version: 'v3', auth })
  return cachedPublisher
}

export type PurchaseInfo = {
  productId: string
  purchaseToken: string
  orderId: string | null
  expiryTime: Date | null
  startTime: Date | null
  autoRenewing: boolean
  acknowledgementState: number
  state: number | null
  raw: unknown
}

export async function verifySubscriptionPurchase(
  productId: string,
  purchaseToken: string,
): Promise<PurchaseInfo> {
  const res = await publisher().purchases.subscriptionsv2.get({
    packageName: env.ANDROID_PACKAGE_NAME,
    token: purchaseToken,
  })
  const sub = res.data
  const item = sub.lineItems?.[0]
  const expiry = item?.expiryTime ? new Date(item.expiryTime) : null
  const start = sub.startTime ? new Date(sub.startTime) : null
  return {
    productId: item?.productId ?? productId,
    purchaseToken,
    orderId: sub.latestOrderId ?? null,
    expiryTime: expiry,
    startTime: start,
    autoRenewing: item?.autoRenewingPlan?.autoRenewEnabled ?? false,
    acknowledgementState: sub.acknowledgementState === 'ACKNOWLEDGED' ? 1 : 0,
    state: null,
    raw: sub,
  }
}

export function purchaseStatusFromInfo(info: PurchaseInfo): string {
  if (!info.expiryTime) return 'expired'
  if (info.expiryTime.getTime() < Date.now()) return 'expired'
  return info.autoRenewing ? 'active' : 'canceled'
}

export async function acknowledgeSubscription(
  productId: string,
  purchaseToken: string,
): Promise<void> {
  await publisher().purchases.subscriptions.acknowledge({
    packageName: env.ANDROID_PACKAGE_NAME,
    subscriptionId: productId,
    token: purchaseToken,
  })
}
