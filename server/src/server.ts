import Fastify from 'fastify'
import cors from '@fastify/cors'
import rateLimit from '@fastify/rate-limit'
import { env } from './env.js'
import { authRoutes } from './routes/auth.js'
import { analyzeRoutes } from './routes/analyze.js'
import { billingRoutes } from './routes/billing.js'
import { meRoutes } from './routes/me.js'

const app = Fastify({
  logger: {
    level: env.NODE_ENV === 'development' ? 'debug' : 'info',
    transport:
      env.NODE_ENV === 'development'
        ? { target: 'pino-pretty', options: { colorize: true } }
        : undefined,
  },
  trustProxy: true,
  bodyLimit: 1024 * 64,
})

await app.register(cors, { origin: true, credentials: true })
await app.register(rateLimit, {
  max: 60,
  timeWindow: '1 minute',
  keyGenerator: (req) =>
    (req.headers['x-device-id'] as string | undefined) ?? req.ip ?? 'anon',
})

app.get('/health', async () => ({ status: 'ok', ts: Date.now() }))

await app.register(authRoutes)
await app.register(analyzeRoutes)
await app.register(billingRoutes)
await app.register(meRoutes)

const port = env.PORT
app.listen({ host: '0.0.0.0', port }).then(() => {
  app.log.info(`API listening on :${port}`)
}).catch((err) => {
  app.log.error(err)
  process.exit(1)
})
