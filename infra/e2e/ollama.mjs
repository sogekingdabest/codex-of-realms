// Deterministic test server. This file is mounted only by compose.e2e.yaml.
import http from 'node:http'
let failures = 0
const models = ['deterministic-chat', 'deterministic-embedding']
http.createServer(async (request, response) => {
  const send = (status, body) => { response.writeHead(status, { 'Content-Type': 'application/json' }); response.end(JSON.stringify(body)) }
  try {
    let text = ''
    for await (const chunk of request) { text += chunk; if (text.length > 2_000_000) { send(413, {}); return } }
    const body = text ? JSON.parse(text) : {}
    if (request.url === '/test/failures') { failures = Number(body.count ?? 0); send(200, { failures }); return }
    if (request.url === '/api/tags') { send(200, { models: models.map(name => ({ name, model: name, size: 1, digest: name })) }); return }
    if (request.url === '/api/version') { send(200, { version: 'deterministic-test-v1' }); return }
    if (request.url === '/api/show') { send(200, { capabilities: ['completion', 'embedding'], model_info: {} }); return }
    if (request.url === '/api/embed' || request.url === '/api/embeddings') {
      if (failures > 0) { failures--; send(503, { error: 'Temporary deterministic failure' }); return }
      const input = Array.isArray(body.input) ? body.input : [body.input ?? body.prompt]
      const vectors = input.map(() => [1, 0, 0, 0, 0, 0, 0, 0])
      send(200, { model: body.model, embeddings: vectors, embedding: vectors[0], prompt_eval_count: input.length }); return
    }
    if (request.url === '/api/chat') {
      const user = body.messages.findLast(message => message.role === 'user')
      const payload = JSON.parse(user.content)
      const passages = payload.untrustedEvidence ?? []
      const selection = { outcome: passages.length ? 'ANSWERED' : 'INSUFFICIENT_EVIDENCE', passageIds: passages.slice(0, 1).map(p => p.passageId) }
      send(200, { model: body.model, created_at: new Date().toISOString(), message: { role: 'assistant', content: JSON.stringify(selection) }, done: true, done_reason: 'stop', total_duration: 1000000, eval_count: 10, eval_duration: 1000000 }); return
    }
    send(404, { error: 'Unknown test endpoint' })
  } catch { send(400, { error: 'Invalid test request' }) }
}).listen(11434, '0.0.0.0')
