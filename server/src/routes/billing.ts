import type { FastifyInstance } from 'fastify'
import { z } from 'zod'
import { prisma } from '../prisma.js'
import { env } from '../env.js'
import {
  acknowledgeSubscription,
  purchaseStatusFromInfo,
  verifySubscriptionPurchase,
} from '../google.js'
import { requireAuth } from '../middleware.js'

const verifySchema = z.object({
  productId: z.string().min(1),
  purchaseToken: z.string().min(10),
})

export async function billingRoutes(app: FastifyInstance) {
  // Called by the Android client right after Play Billing returns a successful purchase.
  // We verify the token with Google Play, then mirror state into our DB.
  app.post(
    '/billing/verify',
    { preHandler: requireAuth },
    async (request, reply) => {
      const parsed = verifySchema.safeParse(request.body)
      if (!parsed.success) return reply.status(400).send({ error: 'BAD_REQUEST' })

      const userId = request.userId!
      const { productId, purchaseToken } = parsed.data

      let info
      try {
        info = await verifySubscriptionPurchase(productId, purchaseToken)
      } catch (e) {
        request.log.error({ err: e }, 'Play verify failed')
        return reply.status(400).send({ error: 'VERIFY_FAILED' })
      }

      const status = purchaseStatusFromInfo(info)

      await prisma.subscription.upsert({
        where: { userId },
        create: {
          userId,
          productId: info.productId,
          purchaseToken,
          orderId: info.orderId,
          status,
          startTime: info.startTime,
          expiryTime: info.expiryTime,
          autoRenewing: info.autoRenewing,
          acknowledgementState: info.acknowledgementState,
          rawState: info.raw as any,
        },
        update: {
          productId: info.productId,
          purchaseToken,
          orderId: info.orderId,
          status,
          startTime: info.startTime,
          expiryTime: info.expiryTime,
          autoRenewing: info.autoRenewing,
          acknowledgementState: info.acknowledgementState,
          rawState: info.raw as any,
        },
      })

      // Acknowledge so Google doesn't auto-refund after 3 days
      if (info.acknowledgementState === 0) {
        await acknowledgeSubscription(info.productId, purchaseToken).catch((e) =>
          request.log.warn({ err: e }, 'acknowledge failed'),
        )
      }

      return reply.send({
        data: {
          status,
          expiryTime: info.expiryTime,
          autoRenewing: info.autoRenewing,
        },
      })
    },
  )

  app.get(
    '/billing/status',
    { preHandler: requireAuth },
    async (request, reply) => {
      const sub = await prisma.subscription.findUnique({ where: { userId: request.userId! } })
      if (!sub) return reply.send({ data: { status: 'none' } })
      const expired = sub.expiryTime ? sub.expiryTime.getTime() < Date.now() : true
      const effective = sub.status === 'active' && expired ? 'expired' : sub.status
      return reply.send({
        data: {
          status: effective,
          productId: sub.productId,
          expiryTime: sub.expiryTime,
          autoRenewing: sub.autoRenewing,
        },
      })
    },
  )

  // Google Pub/Sub push for Real-Time Developer Notifications (RTDN).
  // Configured with a shared secret in the push subscription URL query string:
  //   https://your-server/billing/rtdn?key=RTDN_SHARED_SECRET
  app.post('/billing/rtdn', async (request, reply) => {
    if ((request.query as any)?.key !== env.RTDN_SHARED_SECRET) {
      return reply.status(404).send({ error: 'NOT_FOUND' })
    }

    const body = request.body as { message?: { data?: string } }
    const dataB64 = body?.message?.data
    if (!dataB64) return reply.status(204).send()

    let payload: any
    try {
      payload = JSON.parse(Buffer.from(dataB64, 'base64').toString('utf-8'))
    } catch {
      return reply.status(204).send()
    }

    const sub = payload?.subscriptionNotification
    if (!sub?.purchaseToken || !sub?.subscriptionId) {
      return reply.status(204).send()
    }

    const existing = await prisma.subscription.findUnique({
      where: { purchaseToken: sub.purchaseToken },
    })
    if (!existing) {
      // Unknown token — happens if user never called /billing/verify; safe to drop.
      return reply.status(204).send()
    }

    try {
      const info = await verifySubscriptionPurchase(sub.subscriptionId, sub.purchaseToken)
      const status = purchaseStatusFromInfo(info)
      await prisma.subscription.update({
        where: { purchaseToken: sub.purchaseToken },
        data: {
          status,
          expiryTime: info.expiryTime,
          startTime: info.startTime,
          autoRenewing: info.autoRenewing,
          rawState: info.raw as any,
        },
      })
    } catch (e) {
      request.log.warn({ err: e }, 'RTDN re-verify failed')
    }

    return reply.status(204).send()
  })
}
