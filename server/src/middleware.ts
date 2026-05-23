import type { FastifyRequest, FastifyReply } from 'fastify'
import { verifySessionToken } from './jwt.js'

declare module 'fastify' {
  interface FastifyRequest {
    userId?: string
    userEmail?: string
    deviceId?: string
  }
}

export function requireDevice(request: FastifyRequest, reply: FastifyReply, done: () => void) {
  const id = (request.headers['x-device-id'] as string | undefined)?.trim()
  if (!id || !/^[a-zA-Z0-9_-]{8,128}$/.test(id)) {
    reply.status(400).send({ error: 'INVALID_DEVICE_ID' })
    return
  }
  request.deviceId = id
  done()
}

export async function optionalAuth(request: FastifyRequest, _reply: FastifyReply) {
  const h = request.headers.authorization
  if (!h?.startsWith('Bearer ')) return
  try {
    const p = verifySessionToken(h.slice(7))
    request.userId = p.sub
    request.userEmail = p.email
  } catch {
    // ignore — endpoint treats request as anonymous
  }
}

export async function requireAuth(request: FastifyRequest, reply: FastifyReply) {
  const h = request.headers.authorization
  if (!h?.startsWith('Bearer ')) {
    return reply.status(401).send({ error: 'UNAUTHORIZED' })
  }
  try {
    const p = verifySessionToken(h.slice(7))
    request.userId = p.sub
    request.userEmail = p.email
  } catch {
    return reply.status(401).send({ error: 'UNAUTHORIZED' })
  }
}
