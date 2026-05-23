import type { FastifyInstance } from 'fastify'
import { prisma } from '../prisma.js'
import { env } from '../env.js'
import { optionalAuth, requireDevice } from '../middleware.js'

export async function meRoutes(app: FastifyInstance) {
  // Anonymous-aware status endpoint. Tells the client:
  // - how many trial calls remain on this device
  // - whether the signed-in user (if any) has an active subscription
  app.get('/status', { preHandler: [requireDevice, optionalAuth] }, async (request, reply) => {
    const deviceId = request.deviceId!
    const device = await prisma.device.upsert({
      where: { id: deviceId },
      create: { id: deviceId },
      update: { lastSeenAt: new Date() },
    })

    let sub: { status: string; expiryTime: Date | null } | null = null
    let user: { id: string; email: string; name: string | null; picture: string | null } | null = null

    if (request.userId) {
      const dbUser = await prisma.user.findUnique({
        where: { id: request.userId },
        select: { id: true, email: true, name: true, picture: true },
      })
      if (dbUser) {
        user = dbUser
        const dbSub = await prisma.subscription.findUnique({ where: { userId: dbUser.id } })
        if (dbSub) {
          const expired = dbSub.expiryTime ? dbSub.expiryTime.getTime() < Date.now() : true
          const effective = dbSub.status === 'active' && expired ? 'expired' : dbSub.status
          sub = { status: effective, expiryTime: dbSub.expiryTime }
        }
      }
    }

    const subscriptionActive =
      !!sub && sub.status === 'active' && !!sub.expiryTime && sub.expiryTime.getTime() > Date.now()

    return reply.send({
      data: {
        user,
        subscription: sub,
        subscriptionActive,
        trial: {
          limit: env.TRIAL_LIMIT,
          used: device.trialsUsed,
          remaining: Math.max(0, env.TRIAL_LIMIT - device.trialsUsed),
        },
        deviceBlocked: device.blocked,
      },
    })
  })
}
