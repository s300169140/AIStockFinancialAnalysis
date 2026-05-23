import type { FastifyInstance } from 'fastify'
import { z } from 'zod'
import { prisma } from '../prisma.js'
import { env } from '../env.js'
import { isValidTicker, listTickers } from '../tickers.js'
import { buildPrompt } from '../prompt.js'
import { streamDeepSeek } from '../deepseek.js'
import { optionalAuth, requireDevice } from '../middleware.js'

const bodySchema = z.object({ ticker: z.string().min(1).max(10) })

export async function analyzeRoutes(app: FastifyInstance) {
  app.get('/tickers', async (_request, reply) => {
    return reply.send({ data: listTickers() })
  })

  // Streaming analyze endpoint. Returns Server-Sent-Events:
  //   event: meta  data: { gate: { ... } }
  //   event: delta data: { text: "..." }
  //   event: done  data: { ok: true }
  //   event: error data: { code: "..." }
  app.post(
    '/analyze',
    { preHandler: [requireDevice, optionalAuth] },
    async (request, reply) => {
      const parsed = bodySchema.safeParse(request.body)
      if (!parsed.success) return reply.status(400).send({ error: 'BAD_REQUEST' })

      const ticker = parsed.data.ticker.toUpperCase()
      if (!isValidTicker(ticker)) {
        return reply.status(400).send({ error: 'TICKER_NOT_SUPPORTED' })
      }

      const deviceId = request.deviceId!
      const userId = request.userId ?? null

      // Touch device row + load current trial state
      const device = await prisma.device.upsert({
        where: { id: deviceId },
        create: { id: deviceId },
        update: { lastSeenAt: new Date() },
      })
      if (device.blocked) {
        return reply.status(403).send({ error: 'DEVICE_BLOCKED' })
      }

      // Gate: active subscription bypasses trial
      let hasActiveSub = false
      if (userId) {
        const sub = await prisma.subscription.findUnique({ where: { userId } })
        hasActiveSub =
          !!sub &&
          sub.status === 'active' &&
          !!sub.expiryTime &&
          sub.expiryTime.getTime() > Date.now()
      }

      if (!hasActiveSub && device.trialsUsed >= env.TRIAL_LIMIT) {
        return reply.status(402).send({
          error: 'TRIAL_EXHAUSTED',
          trialLimit: env.TRIAL_LIMIT,
          trialsUsed: device.trialsUsed,
        })
      }

      // Reserve the trial slot up front to avoid races on rapid taps
      let reservedTrial = false
      if (!hasActiveSub) {
        await prisma.device.update({
          where: { id: deviceId },
          data: { trialsUsed: { increment: 1 } },
        })
        reservedTrial = true
      }

      reply.raw.setHeader('Content-Type', 'text/event-stream; charset=utf-8')
      reply.raw.setHeader('Cache-Control', 'no-cache, no-transform')
      reply.raw.setHeader('Connection', 'keep-alive')
      reply.raw.setHeader('X-Accel-Buffering', 'no')
      reply.raw.flushHeaders?.()

      const send = (event: string, data: unknown) => {
        reply.raw.write(`event: ${event}\n`)
        reply.raw.write(`data: ${JSON.stringify(data)}\n\n`)
      }

      send('meta', {
        ticker,
        gate: hasActiveSub
          ? { mode: 'subscription' }
          : { mode: 'trial', remaining: Math.max(0, env.TRIAL_LIMIT - (device.trialsUsed + 1)) },
      })

      const prompt = buildPrompt(ticker)
      let promptTokens = 0
      let completionTokens = 0
      let succeeded = false
      let errorCode: string | null = null

      try {
        for await (const chunk of streamDeepSeek(prompt)) {
          if (chunk.type === 'delta') {
            send('delta', { text: chunk.text })
          } else if (chunk.type === 'usage') {
            promptTokens = chunk.promptTokens
            completionTokens = chunk.completionTokens
          } else if (chunk.type === 'error') {
            errorCode = 'DEEPSEEK_ERROR'
            send('error', { code: errorCode, message: chunk.message })
            break
          } else if (chunk.type === 'done') {
            succeeded = true
            send('done', { ok: true })
          }
        }
      } catch (e) {
        request.log.error({ err: e }, 'analyze stream failed')
        errorCode = 'STREAM_FAILED'
        try {
          send('error', { code: errorCode })
        } catch {}
      } finally {
        reply.raw.end()
      }

      // Refund trial slot on failure (only if user got no usable content)
      if (reservedTrial && !succeeded) {
        await prisma.device.update({
          where: { id: deviceId },
          data: { trialsUsed: { decrement: 1 } },
        }).catch(() => {})
      }

      await prisma.analysisLog.create({
        data: {
          userId,
          deviceId,
          ticker,
          promptTokens: promptTokens || null,
          completionTokens: completionTokens || null,
          status: succeeded ? 'success' : 'error',
          errorCode,
        },
      }).catch((e) => request.log.error({ err: e }, 'analysisLog create failed'))
    },
  )
}
