import type { FastifyInstance } from 'fastify'
import { z } from 'zod'
import { prisma } from '../prisma.js'
import { verifyGoogleIdToken } from '../google.js'
import { signSessionToken } from '../jwt.js'
import { requireDevice } from '../middleware.js'

const googleSchema = z.object({ idToken: z.string().min(10) })

export async function authRoutes(app: FastifyInstance) {
  app.post('/auth/google', { preHandler: requireDevice }, async (request, reply) => {
    const parsed = googleSchema.safeParse(request.body)
    if (!parsed.success) return reply.status(400).send({ error: 'BAD_REQUEST' })

    let g
    try {
      g = await verifyGoogleIdToken(parsed.data.idToken)
    } catch (e) {
      request.log.warn({ err: e }, 'Google ID token verify failed')
      return reply.status(401).send({ error: 'INVALID_ID_TOKEN' })
    }

    const user = await prisma.user.upsert({
      where: { googleSub: g.sub },
      create: {
        googleSub: g.sub,
        email: g.email,
        name: g.name,
        picture: g.picture,
      },
      update: {
        email: g.email,
        name: g.name,
        picture: g.picture,
        lastLoginAt: new Date(),
      },
    })

    const deviceId = request.deviceId!
    await prisma.device.upsert({
      where: { id: deviceId },
      create: { id: deviceId },
      update: { lastSeenAt: new Date() },
    })
    await prisma.userDevice.upsert({
      where: { userId_deviceId: { userId: user.id, deviceId } },
      create: { userId: user.id, deviceId },
      update: {},
    })

    const token = signSessionToken({ sub: user.id, email: user.email })
    return reply.send({
      data: {
        token,
        user: { id: user.id, email: user.email, name: user.name, picture: user.picture },
      },
    })
  })
}
