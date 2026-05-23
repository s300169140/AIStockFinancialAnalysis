import { fetch } from 'undici'
import { env } from './env.js'

const DEEPSEEK_URL = 'https://api.deepseek.com/v1/chat/completions'

export type DeepSeekChunk =
  | { type: 'delta'; text: string }
  | { type: 'usage'; promptTokens: number; completionTokens: number }
  | { type: 'done' }
  | { type: 'error'; message: string }

export async function* streamDeepSeek(prompt: string): AsyncGenerator<DeepSeekChunk, void, unknown> {
  const res = await fetch(DEEPSEEK_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${env.DEEPSEEK_API_KEY}`,
    },
    body: JSON.stringify({
      model: env.DEEPSEEK_MODEL,
      messages: [{ role: 'user', content: prompt }],
      stream: true,
      temperature: 0.4,
    }),
  })

  if (!res.ok || !res.body) {
    const text = await res.text().catch(() => '')
    yield { type: 'error', message: `DeepSeek ${res.status}: ${text.slice(0, 300)}` }
    return
  }

  const decoder = new TextDecoder()
  let buf = ''
  let lastUsage: { promptTokens: number; completionTokens: number } | null = null

  for await (const chunk of res.body as any) {
    buf += decoder.decode(chunk as Uint8Array, { stream: true })
    const lines = buf.split('\n')
    buf = lines.pop() ?? ''

    for (const line of lines) {
      const trimmed = line.trim()
      if (!trimmed.startsWith('data:')) continue
      const data = trimmed.slice(5).trim()
      if (data === '[DONE]') {
        if (lastUsage) yield { type: 'usage', ...lastUsage }
        yield { type: 'done' }
        return
      }
      try {
        const json = JSON.parse(data)
        const delta = json?.choices?.[0]?.delta?.content
        if (typeof delta === 'string' && delta.length > 0) {
          yield { type: 'delta', text: delta }
        }
        if (json?.usage) {
          lastUsage = {
            promptTokens: json.usage.prompt_tokens ?? 0,
            completionTokens: json.usage.completion_tokens ?? 0,
          }
        }
      } catch {
        // malformed line — skip
      }
    }
  }

  if (lastUsage) yield { type: 'usage', ...lastUsage }
  yield { type: 'done' }
}
