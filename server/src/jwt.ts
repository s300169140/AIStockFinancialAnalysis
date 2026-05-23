import jwt from 'jsonwebtoken'
import { env } from './env.js'

export type JwtPayload = { sub: string; email: string }

export function signSessionToken(payload: JwtPayload): string {
  return jwt.sign(payload, env.JWT_SECRET, { expiresIn: '30d' })
}

export function verifySessionToken(token: string): JwtPayload {
  const decoded = jwt.verify(token, env.JWT_SECRET) as jwt.JwtPayload
  if (typeof decoded.sub !== 'string' || typeof decoded.email !== 'string') {
    throw new Error('Invalid token payload')
  }
  return { sub: decoded.sub, email: decoded.email }
}
