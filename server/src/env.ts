import { z } from 'zod'

const schema = z.object({
  PORT: z.coerce.number().default(3001),
  NODE_ENV: z.enum(['development', 'production', 'test']).default('production'),
  DATABASE_URL: z.string().min(1),
  JWT_SECRET: z.string().min(32),
  DEEPSEEK_API_KEY: z.string().min(1),
  DEEPSEEK_MODEL: z.string().default('deepseek-chat'),
  GOOGLE_WEB_CLIENT_ID: z.string().min(1),
  GOOGLE_SERVICE_ACCOUNT_JSON: z.string().min(1),
  ANDROID_PACKAGE_NAME: z.string().min(1),
  PRO_SUBSCRIPTION_PRODUCT_ID: z.string().default('pro_monthly'),
  TRIAL_LIMIT: z.coerce.number().default(3),
  RTDN_SHARED_SECRET: z.string().min(8),
})

const parsed = schema.safeParse(process.env)
if (!parsed.success) {
  console.error('Invalid env:', parsed.error.flatten().fieldErrors)
  process.exit(1)
}

export const env = parsed.data
